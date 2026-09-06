package com.ludwici.cobblemonstreamermode.client;

import com.cobblemon.mod.common.client.gui.battle.subscreen.*;

import java.util.List;

public class PollData {
    private int voteCount;
    private Actions currentAction;

    public Actions getCurrentAction() {
        return currentAction;
    }

    public void setCurrentAction(Actions currentAction) {
        this.currentAction = currentAction;
        voteCount = 0;
    }

    @SuppressWarnings("DuplicateBranchesInSwitch")
    public List<String> getPollVariants() {
        return switch (currentAction) {
            case Actions.MOVE ->
                List.of("1", "2", "3", "4");
            case GENERAL ->
                List.of("1", "2", "3", "4");
            case TARGET->
                List.of("1", "2", "3", "4", "5", "6");
            case SWITCH_POKEMON->
                List.of("1", "2", "3", "4", "5", "6");
            case FORFEIT->
                List.of("");
        };
    }

    public enum Actions {
        MOVE, // selection between moves 1+
        GENERAL, // selection between Fight, Switch, Catch, Run
        TARGET, // selection from many pokemon to attack?
        SWITCH_POKEMON, // selection between your pokemon 1-6 or cancel if single pokemon
        FORFEIT; // selection where you have pvp?

        public static Actions toAction(BattleActionSelection selection) {
            if (selection instanceof BattleMoveSelection) {
                return MOVE;
            }
            if (selection instanceof BattleGeneralActionSelection) {
                return GENERAL;
            }
            if (selection instanceof BattleTargetSelection) {
                return TARGET;
            }
            if (selection instanceof BattleSwitchPokemonSelection) {
                return SWITCH_POKEMON;
            }
            if (selection instanceof ForfeitConfirmationSelection) {
                return FORFEIT;
            }
            throw new IllegalArgumentException("Unknown BattleActionSelection: " + selection.getClass());
        }
    }
}
