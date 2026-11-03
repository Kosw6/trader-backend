"""Local-only additive migration and disposable API verification. Never prints credentials."""
import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "trader-ai/scripts"))
import psycopg
from local_db_roles import local_admin_dsn


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["inspect", "apply", "verify", "cleanup-ui-fixture"])
    args = parser.parse_args()
    with psycopg.connect(local_admin_dsn(), connect_timeout=5) as conn:
        conn.execute("SET statement_timeout = '15s'")
        conn.execute("SET lock_timeout = '3s'")
        if args.mode == "inspect":
            conn.execute("SET TRANSACTION READ ONLY")
            exists, count = conn.execute("SELECT to_regclass('public.note_stock_tag') IS NOT NULL, (SELECT count(*) FROM public.note)").fetchone()
            print({"localNoteCount": count, "tickerTableExists": exists})
        elif args.mode == "apply":
            if conn.execute("SELECT to_regclass('public.note_stock_tag') IS NOT NULL").fetchone()[0]:
                raise RuntimeError("Already present; refusing to rerun migration")
            sql = (ROOT / "trader-controller/db/migration/V041__multiple_note_tickers.sql").read_text(encoding="utf-8")
            conn.execute(sql)
            conn.commit()
            print({"migration": "V041", "committed": True, "legacyNotesRewritten": 0})
        elif args.mode == "cleanup-ui-fixture":
            cleanup_ui_fixture(conn)
        else:
            verify(conn)


def cleanup_ui_fixture(conn):
    import httpx
    title = "[기능 확인] 애플·엔비디아 비교 노트"
    content = "다중 티커 저장과 수정 확인용 로컬 테스트 노트입니다.\n실제 투자 판단이 아닌 기능 검증 자료입니다."
    rows = conn.execute("SELECT n.id FROM note n JOIN users u ON u.id=n.user_id WHERE u.login_id=%s AND n.subject=%s AND n.content=%s AND n.note_date='2026-10-19'", ("test1", title, content)).fetchall()
    assert len(rows) == 1, "UI fixture must match exactly one isolated note"
    login, password = conn.execute("SELECT login_id,password FROM users WHERE login_id=%s", ("test1",)).fetchone()
    conn.rollback()
    with httpx.Client(base_url="http://127.0.0.1:18080", timeout=30, trust_env=False) as client:
        assert client.post("/api/login/signin", json={"loginId":login,"password":password}).status_code == 200
        del password
        assert client.delete(f"/api/note/delete/{rows[0][0]}").status_code == 204
        assert client.get("/api/note").status_code == 200
    print("Removed only the exact UI fixture created in this task; existing user notes preserved")


def verify(conn):
    import httpx
    import uuid
    # Only the explicitly approved local test1 account, through ordinary login.
    login, password = conn.execute("SELECT login_id, password FROM users WHERE login_id = %s", ("test1",)).fetchone()
    conn.rollback()
    title = "[multi-ticker verification] " + uuid.uuid4().hex[:12]
    created = None
    with httpx.Client(base_url="http://127.0.0.1:18080", timeout=30, trust_env=False) as client:
        response = client.post("/api/login/signin", json={"loginId": login, "password": password})
        assert response.status_code == 200, "Normal local login failed"
        del password
        draft = {"subject": title, "content": "Local disposable ticker verification.\nPreserve exact text.", "noteDate": "2026-10-19", "stockSymbs": ["AAPL", "NVDA", "AAPL", "MSFT"]}
        try:
            response = client.post("/api/note/save", json=draft)
            assert response.status_code == 200, f"create status {response.status_code}"
            created = response.json()
            assert isinstance(created, int)
            stored = client.get(f"/api/note/{created}").json()
            assert stored["stockSymbs"] == ["AAPL", "NVDA", "MSFT"]
            assert stored["stockSymb"] == "AAPL" and stored["noteDate"] == draft["noteDate"]
            # Independent DB read after the API transaction has committed.
            with psycopg.connect(local_admin_dsn(), options="-c default_transaction_read_only=on") as read:
                assert [r[0] for r in read.execute("SELECT symbol FROM note_stock_tag WHERE note_id=%s ORDER BY tag_order", (created,))] == stored["stockSymbs"]
            for ticker in stored["stockSymbs"]:
                for path, params in [("/api/note/stock", {}), ("/api/note/range", {"startDate": "2026-10-19", "endDate": "2026-10-19"})]:
                    response = client.get(path, params={"stockName": ticker, "size": 100, **params})
                    assert response.status_code == 200
                    assert sum(note["id"] == created for note in response.json()["content"]) == 1
            assert any(note["id"] == created and note["stockSymbs"] == stored["stockSymbs"] for note in client.get("/api/note", params={"size":100}).json()["content"])
            # Legacy edit cannot drop secondary tags when only the text changes.
            legacy = {**draft, "stockSymb": "AAPL", "subject": title + " edited"}
            del legacy["stockSymbs"]
            assert client.put(f"/api/note/update/{created}", json=legacy).status_code == 200
            assert client.get(f"/api/note/{created}").json()["stockSymbs"] == stored["stockSymbs"]
            assert client.put(f"/api/note/update/{created}", json={**legacy, "stockSymb": "TSLA"}).status_code == 400
            # Remove a primary tag, then clear everything; stale legacy scalar must not revive it.
            for symbols in [["NVDA", "MSFT"], []]:
                assert client.put(f"/api/note/update/{created}", json={**draft, "stockSymbs":symbols, "stockSymb":"AAPL"}).status_code == 200
                assert client.get(f"/api/note/{created}").json()["stockSymbs"] == symbols
            assert client.get(f"/api/note/{created}").json()["stockSymb"] is None
            assert not any(n["id"] == created for n in client.get("/api/note/stock", params={"stockName":"NVDA", "size":100}).json()["content"])
            for symbols in [["AAPL,NVDA"], [None], ["x"*65], ["AAPL"]*21]:
                assert client.put(f"/api/note/update/{created}", json={**draft, "stockSymbs":symbols}).status_code == 400
            print("PASS: committed DB rows, 3 ticker/range filters, list/detail, duplicate, legacy edit protection, removal/clear, invalid values")
        finally:
            # Only delete the isolated note created above, with exact title ownership check.
            if created:
                row = client.get(f"/api/note/{created}")
                assert row.status_code == 200 and row.json()["subject"].startswith(title)
                assert client.delete(f"/api/note/delete/{created}").status_code == 204
                print("Disposable verification note removed; existing user notes untouched")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        # Assertions contain only fixed messages/status codes. Never dump a connection exception.
        print(type(exc).__name__ + (": " + str(exc) if isinstance(exc, AssertionError) else ": local check failed"))
        sys.exit(1)
