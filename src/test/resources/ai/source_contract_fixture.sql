-- 독립 테스트 schema 전용. 실제 서비스 전체 schema를 대체하지 않는다.
CREATE TABLE directory(id bigint primary key,user_id bigint,team_id bigint);
CREATE TABLE page(id bigint primary key,directory_id bigint references directory(id) ON DELETE CASCADE,user_id bigint);
CREATE TABLE note(id bigint primary key,user_id bigint,subject text,content text,stock_symb text,note_date date);
CREATE TABLE node(id bigint primary key,page_id bigint references page(id) ON DELETE CASCADE,subject text,content text,symb text,record_date date,x float,y float);
CREATE TABLE edge(id bigint primary key,source_id bigint references node(id) ON DELETE CASCADE,target_id bigint references node(id) ON DELETE CASCADE,page_id bigint references page(id) ON DELETE CASCADE,label text,stroke text);
CREATE TABLE node_note_link(id bigint primary key,node_id bigint references node(id) ON DELETE CASCADE,note_id bigint references note(id) ON DELETE CASCADE,unique(node_id,note_id));
CREATE TABLE user_team(id bigint primary key,user_id bigint,team_id bigint);
INSERT INTO directory VALUES(1,1,null),(2,1,7),(3,2,null);
INSERT INTO page VALUES(1,1,1),(2,2,1),(3,3,2);
INSERT INTO user_team VALUES(1,1,7),(2,2,7);
INSERT INTO note VALUES(1,1,'내 노트','본문',null,'2026-10-02'),(2,2,'다른 노트','본문',null,'2026-10-02');
INSERT INTO node VALUES(1,1,'개인','본문',null,'2026-10-02',0,0),(2,2,'팀','본문',null,'2026-10-02',0,0),(3,2,'이웃','본문',null,'2026-10-02',0,0);
INSERT INTO node_note_link VALUES(1,2,2);
INSERT INTO edge VALUES(1,2,3,2,null,'red');
