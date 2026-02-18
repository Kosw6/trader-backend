package com.example.trader.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Transactional
@SpringBootTest
class TeamServiceUnitTest {
    @Autowired
    private UserService userService;
    @Autowired
    private TeamService teamService;

    void createTeam(){

    }
    void updateTeamName(){

    }
    void getTeamRole(){

    }
    void changeTeamRole(){

    }
    void joinTeam(){

    }

    void exitTeam(){

    }
    void checkTeamRequest(){

    }

    void deleteTeam(){

    }
    void findTeam(){

    }
    void findAllMyTeam(){

    }
}