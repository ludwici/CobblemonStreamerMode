package com.ludwici.cobblemonstreamermode.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BattleManager {

    public static final BattleManager INSTANCE = new BattleManager();

    private boolean currentStatus;

    private Map<String, Integer> movesPoll = new HashMap<>();
    private List<String> movesPollUsers = new ArrayList<>();

    private BattleManager(){}

    public boolean isCurrentStatus() {
        return currentStatus;
    }

    public Map<String, Integer> getMovesPoll() {
        return movesPoll;
    }

    public void movePoll(String userId, String moveId) {
        if (movesPollUsers.contains(userId)) {
            return;
        }

        movesPoll.merge(moveId, 1, Integer::sum);
        // TODO: Uncomment this
//        movesPollUsers.add(userId);
    }

    public void setCurrentStatus(boolean currentStatus) {
        this.currentStatus = currentStatus;
        if (!currentStatus) {
            movesPoll.clear();
            movesPollUsers.clear();
        }
    }
}
