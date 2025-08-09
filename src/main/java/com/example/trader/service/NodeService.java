package com.example.trader.service;

import com.example.trader.dto.NodeRequestDto;
import com.example.trader.dto.NodeResponseDto;
import com.example.trader.entity.Node;
import com.example.trader.entity.Note;
import com.example.trader.entity.Page;
import com.example.trader.repository.EdgeRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.repository.NoteRepository;
import com.example.trader.repository.PageRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NodeService {

    private final NodeRepository nodeRepository;
    private final PageRepository pageRepository;
    private final NoteRepository noteRepository;
    private final EdgeRepository edgeRepository;

    @Transactional
    public NodeResponseDto createNode(NodeRequestDto dto) {
        Page page = pageRepository.findById(dto.getPageId())
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        Note note = noteRepository.findById(dto.getNoteId()).orElseThrow(() -> new IllegalArgumentException("note not found"));
        Node node = Node.builder().subject(dto.getSubject())
                .x(dto.getX())
                .y(dto.getY())
                .symb(dto.getSymb())
                .note(note)
                .content(dto.getContent())
                .build();
        page.addNode(node);
        Node savedNode = nodeRepository.save(node);
        return toResponseDto(savedNode);
    }

    @Transactional
    public NodeResponseDto updateNode(Long id, NodeRequestDto dto, Long pageId) {
        Node node = nodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Node not found"));
        Page page = pageRepository.findById(dto.getPageId())
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        Note note = noteRepository.findById(dto.getNoteId())
                .orElseThrow(() -> new IllegalArgumentException("Note not found"));
        node.updateFromDto(dto, page, note);
        return toResponseDto(node);
    }



    @Transactional
    public void deleteNode(Long id) {
        edgeRepository.deleteByNodeId(id);
        nodeRepository.deleteById(id);
    }

    @Transactional
    public NodeResponseDto createNode(NodeRequestDto dto, Long pageId) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        Node node = Node.builder()
                .x(dto.getX())
                .y(dto.getY())
                .subject(dto.getSubject())
                .content(dto.getContent())
                .symb(dto.getSymb())
                .page(page)
                // noteId가 있다면 note도 조회해서 set
                .build();
        Node saved = nodeRepository.save(node);
        return toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public List<NodeResponseDto> findAllByPageId(Long pageId) {
        return nodeRepository.findByPageId(pageId).stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public NodeResponseDto findById(Long id) {
        return toResponseDto(nodeRepository.findById(id).orElseThrow(()-> new IllegalArgumentException()));
    }

    private NodeResponseDto toResponseDto(Node node) {
        return NodeResponseDto.builder()
                .id(node.getId())
                .x(node.getX())
                .y(node.getY())
                .subject(node.getSubject())
                .content(node.getContent())
                .symb(node.getSymb())
                .noteId(node.getNote() != null ? node.getNote().getId() : null)
                .pageId(node.getPage() != null ? node.getPage().getId() : null)
                .build();
    }
}
