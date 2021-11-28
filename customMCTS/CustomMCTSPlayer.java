package players.customMCTS;

import core.GameState;
import players.optimisers.ParameterizedPlayer;
import players.Player;
import utils.ElapsedCpuTimer;
import utils.Types;

import java.util.ArrayList;
import java.io.*;

import java.util.Random;

//import utils.Vector2d;
//import java.util.Arrays;

public class CustomMCTSPlayer extends ParameterizedPlayer {

    /**
     * Random generator.
     */
    private Random m_rnd;

    /**
     * All actions available.
     */
    public Types.ACTIONS[] actions;

    /**
     * Params for this MCTS
     */
    public CustomMCTSParams params;

    /**
     * Indicate if game is partially observable
     */
    //boolean isPartObserv = false;

    /**
     * A copy of the board to save board tiles that may be removed by the fog in the following game state.
     */
    private Types.TILETYPE[][] boardCopy;

    /**
     * Variables for player modelling
      */
    private Types.TILETYPE[] agents = {Types.TILETYPE.AGENT0, Types.TILETYPE.AGENT1, Types.TILETYPE.AGENT2, Types.TILETYPE.AGENT3};
    private int[][] startCoordinates = {{1,1},{1,9},{9,9},{9,1}};
    private int[][] directions = {{0,0}, {0,-1},{0,1},{-1,0},{1,0}};


    public CustomMCTSPlayer(long seed, int id) {
        this(seed, id, new CustomMCTSParams());
    }

    public CustomMCTSPlayer(long seed, int id, CustomMCTSParams params) {
        super(seed, id, params);
        reset(seed, id);

        //initialise array of actions
        ArrayList<Types.ACTIONS> actionsList = Types.ACTIONS.all();
        actions = new Types.ACTIONS[actionsList.size()];
        int i = 0;
        for (Types.ACTIONS act : actionsList) {
            actions[i++] = act;
        }

        // load and store opponent move data from file
        loadOpponentData();
    }

    @Override
    public void reset(long seed, int playerID) {
        super.reset(seed, playerID);
        m_rnd = new Random(seed);

        this.params = (CustomMCTSParams) getParameters();
        if (this.params == null) {
            this.params = new CustomMCTSParams();
            super.setParameters(this.params);
        }

        //reset board for every new seed
        this.boardCopy = null;

        // increment match number and reset turn number for new match
        params.gameNum += 1;
        params.gameNum %= 20;
        params.turnNum = -1;
    }

    @Override
    public Types.ACTIONS act(GameState gs) {
        // TODO update gs
        if (gs.getGameMode().equals(Types.GAME_MODE.TEAM_RADIO)){
            int[] msg = gs.getMessage();
        }

        updateOpponentMoves(gs);
        params.turnNum += 1;

        // update gs board
        //this.boardCopy = CustomUtils.updateBoard(gs, this.boardCopy);
        //this.boardCopy = CustomUtils.copyBoard(gs.getBoard(), this.boardCopy);

        ElapsedCpuTimer ect = new ElapsedCpuTimer();
        ect.setMaxTimeMillis(params.num_time);

        // Number of actions available
        int num_actions = actions.length;

        // Root of the tree
        CustomSingleTreeNode m_root = new CustomSingleTreeNode(params, m_rnd, num_actions, actions);
        m_root.setRootGameState(gs);

        //Determine the action using MCTS...
        m_root.mctsSearch(ect);

        //Determine the best action to take and return it.
        int action = m_root.mostVisitedAction();

        // TODO update message memory

        //... and return it.
        return actions[action];
    }

    private void loadOpponentData(){
        params.opponentMoves = new int[4][][];
        try{
            ObjectInputStream objectInputStream =
                    new ObjectInputStream(new FileInputStream("opponentMoves.data"));

            params.opponentMoves[0] = (int[][]) objectInputStream.readObject();
            for(int i = 1; i < 4; i++){
                params.opponentMoves[i] = params.opponentMoves[0].clone();
            }
            //System.out.println(Arrays.toString(params.opponentMoves[1][1]));
            objectInputStream.close();
        }
        catch (IOException | ClassNotFoundException e) {
            System.out.println("Error");
        }
    }

    private void updateOpponentMoves(GameState gs){
        //Vector2d myCoor = gs.getPosition();
        int myId = gs.getPlayerId() - Types.TILETYPE.AGENT0.getKey();
        //System.out.println(myId);

        // if start of game, initialise player starting coordinates
        if(params.turnNum == -1){
            params.opponentCoordinates = new int[4][2];
            int i = 0;
            for(int[] coordinate : startCoordinates){
                if(i != myId){
                    params.opponentCoordinates[i][0] = coordinate[0];
                    params.opponentCoordinates[i][1] = coordinate[1];
                }
                i += 1;
            }
            return;
        }

        Types.TILETYPE[][] board = gs.getBoard();

        for(int i = 0; i < params.opponentCoordinates.length; i++){
            if(i == myId){ continue; }

            // get player coordinates
            int x = params.opponentCoordinates[i][0];
            int y = params.opponentCoordinates[i][1];

            // get new opponent coordinate and store action in opponent model
            for(int j = directions.length-1; j >= 0 ; j--){
                int[] direction = directions[j];
                int x2 = direction[0] + x;
                int y2 = direction[1] + y;
                if(x2 >= 0 && x2 < board[0].length && y2 >= 0 && y2 < board.length){
                    if(board[y2][x2] == agents[i]){
                        /*
                        if(params.turnNum == 0){
                            System.out.println('x');
                        }
                         */
                        params.opponentCoordinates[i][0] = x2;
                        params.opponentCoordinates[i][1] = y2;
                        params.opponentMoves[i][params.turnNum][params.gameNum] = j;
                        break;
                    }
                    else if(board[y2][x2] == Types.TILETYPE.BOMB && board[x][y] == agents[i]){
                        // add bomb
                        params.opponentMoves[i][params.turnNum][params.gameNum] = 5;
                        break;
                    }
                }
            }
        }
    }

    @Override
    public int[] getMessage() {
        // default message
        int[] message = new int[Types.MESSAGE_LENGTH];
        message[0] = 1;
        return message;
    }

    @Override
    public Player copy() {
        return new CustomMCTSPlayer(seed, playerID, params);
    }
}