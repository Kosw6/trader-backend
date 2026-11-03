package com.example.trader.service;

import com.example.trader.entity.Page;
import com.example.trader.exception.BaseException;
import com.example.trader.repository.DirectoryRepository;
import com.example.trader.repository.PageRepository;
import com.example.trader.repository.UserTeamRepository;
import com.example.trader.realtime.outbox.TeamTreeChangeStager;
import com.example.trader.security.service.SecurityService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PageServiceAccessTest {
    private final PageRepository pages = mock(PageRepository.class);
    private final PageService service = new PageService(pages, mock(DirectoryRepository.class),
            mock(SecurityService.class), mock(UserTeamRepository.class), mock(TeamTreeChangeStager.class));

    @Test
    void ownerCanReadOwnPage() {
        Page page = mock(Page.class);
        when(page.getId()).thenReturn(7L);
        when(page.getTitle()).thenReturn("Owned page");
        when(pages.findByIdAndUserId(7L, 11L)).thenReturn(Optional.of(page));
        assertEquals(7L, service.getPage(7L, 11L).getId());
        verify(pages).findByIdAndUserId(7L, 11L);
        verify(pages, never()).findById(anyLong());
    }

    @Test
    void otherUserCannotReadExistingPage() {
        when(pages.findById(7L)).thenReturn(Optional.of(mock(Page.class)));
        when(pages.findByIdAndUserId(7L, 22L)).thenReturn(Optional.empty());
        assertThrows(BaseException.class, () -> service.getPage(7L, 22L));
        verify(pages, never()).findById(anyLong());
    }

    @Test
    void missingPageUsesSameDeniedBoundary() {
        when(pages.findByIdAndUserId(99L, 11L)).thenReturn(Optional.empty());
        assertThrows(BaseException.class, () -> service.getPage(99L, 11L));
        verify(pages, never()).findById(anyLong());
    }
}
