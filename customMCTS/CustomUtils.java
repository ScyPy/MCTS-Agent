package players.customMCTS;

import core.GameState;
import utils.Types;

public class CustomUtils{


    // update game state board by replacing fog with previous observations
    public static Types.TILETYPE[][] updateBoard(GameState gs, Types.TILETYPE[][] boardCopy){
        // get current game state board
        Types.TILETYPE[][] board = gs.getBoard();
        //int x = 0;

        if (boardCopy != null){
            for(int i = 0; i < board.length; i++){
                for(int j = 0; j < board[i].length; j++) {
                    /*
                    if (boardCopy[i][j] == Types.TILETYPE.FOG){
                        x ++;
                    }

                     */

                    // if tile is of type fog, update it to previous observation
                    if (board[i][j] == Types.TILETYPE.FOG) {
                        switch (boardCopy[i][j]) {
                            /*

                            case KICK:
                                gs.addPowerUp(j, i, Types.TILETYPE.KICK, true);
                                break;
                            case INCRRANGE:
                                gs.addPowerUp(j, i, Types.TILETYPE.INCRRANGE, true);
                                break;
                            case EXTRABOMB:
                                gs.addPowerUp(j, i, Types.TILETYPE.EXTRABOMB, true);
                                break;
                            case FLAMES:
                                break;
                             */
                            /*
                            case BOMB:
                                gs.addBomb(j, i, gs.getBombBlastStrength()[i][j], gs.getBombLife()[i][j], 0, true);
                                break;

                            case WOOD:
                                gs.addObject(j, i, Types.TILETYPE.WOOD);
                                break;
                            */

                            case RIGID:
                                gs.addObject(j, i, Types.TILETYPE.RIGID);
                                break;
                            case PASSAGE:
                                gs.addObject(j, i, Types.TILETYPE.PASSAGE);
                                break;
                        }
                    }
                }
            }
        }

        //System.out.println(x);
        return board;
    }

    // create a deep copy of the board
    public static Types.TILETYPE[][] copyBoard(Types.TILETYPE[][] board, Types.TILETYPE[][] boardCopy){
        boardCopy = new Types.TILETYPE[board.length][board[0].length];
        for (int i = 0; i < board.length; i++){
            for (int j = 0; j < board[i].length; j++){
                boardCopy[i][j] = board[i][j];
            }
        }
        return boardCopy;
    }

}