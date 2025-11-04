package com.technicalchallenge.service;

import com.technicalchallenge.model.UserPrivilege;
import com.technicalchallenge.repository.UserPrivilegeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserPrivilegeServiceTest {

    @Mock
    private UserPrivilegeRepository userPrivilegeRepository;

    @InjectMocks
    private UserPrivilegeService userPrivilegeService;

    private UserPrivilege userPrivilege;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userPrivilege = new UserPrivilege();
        userPrivilege.setUserId(1L);
        userPrivilege.setPrivilegeId(10L);
    }

    @Test
    void getAllUserPrivileges_returnsList() {
        when(userPrivilegeRepository.findAll()).thenReturn(List.of(userPrivilege));

        List<UserPrivilege> result = userPrivilegeService.getAllUserPrivileges();

        assertEquals(1, result.size());
        verify(userPrivilegeRepository).findAll();
    }

    @Test
    void getUserPrivilegeById_returnsPrivilege() {
        when(userPrivilegeRepository.findById(1L)).thenReturn(Optional.of(userPrivilege));

        Optional<UserPrivilege> result = userPrivilegeService.getUserPrivilegeById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getUserId());
    }

    @Test
    void saveUserPrivilege_savesSuccessfully() {
        when(userPrivilegeRepository.save(userPrivilege)).thenReturn(userPrivilege);

        UserPrivilege result = userPrivilegeService.saveUserPrivilege(userPrivilege);

        assertNotNull(result);
        verify(userPrivilegeRepository).save(userPrivilege);
    }

    @Test
    void deleteUserPrivilege_executesRepositoryCall() {
        doNothing().when(userPrivilegeRepository).deleteById(1L);

        userPrivilegeService.deleteUserPrivilege(1L);

        verify(userPrivilegeRepository).deleteById(1L);
    }
}
