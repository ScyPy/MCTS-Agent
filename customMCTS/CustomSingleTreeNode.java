package players.customMCTS;

import core.GameState;
import players.customMCTS.heuristics.CMHeuristic;
import players.heuristics.AdvancedHeuristic;
import players.heuristics.CustomHeuristic;
import players.heuristics.StateHeuristic;
import utils.ElapsedCpuTimer;
import utils.Types;
import utils.Utils;
import utils.Vector2d;

import java.util.ArrayList;
import java.util.Random;
import  java.util.Arrays;

public class CustomSingleTreeNode {
    public CustomMCTSParams params;

    private CustomSingleTreeNode parent;
    private CustomSingleTreeNode[] children;
    private double totValue;
    private int nVisits;
    private Random m_rnd;
    private int m_depth;
    private double[] bounds = new double[]{Double.MAX_VALUE, -Double.MAX_VALUE};
    private int childIdx;
    private int fmCallsCount;

    private int num_actions;
    private Types.ACTIONS[] actions;

    private GameState rootState;
    private StateHeuristic rootStateHeuristic;

    //private int[][][] pessimismBoard;
    //private double[] agentScores = new double[4];

    /**
     * A copy of the board to save board tiles that may be removed by the fog in the following game state.
     */
    //private Types.TILETYPE[][] boardCopy;

    /**
     * Sum of all squared values for the node
     */
    private double squaredTotValue;

    /**
     * Progressive bias value
     */
    private double heuristicValue;

    private Types.ACTIONS chosenAct;

    private ArrayList<CustomSingleTreeNode> alternativeNodes;
    private double amafResults;
    private int amafVisits;



    CustomSingleTreeNode(CustomMCTSParams p, Random rnd, int num_actions, Types.ACTIONS[] actions) {
        this(p, null, -1, rnd, num_actions, actions, 0, null);
    }

    private CustomSingleTreeNode(CustomMCTSParams p, CustomSingleTreeNode parent, int childIdx, Random rnd, int num_actions,
                                 Types.ACTIONS[] actions, int fmCallsCount, StateHeuristic sh) {
        this.params = p;
        this.fmCallsCount = fmCallsCount;
        this.parent = parent;
        this.m_rnd = rnd;
        this.num_actions = num_actions;
        this.actions = actions;
        children = new CustomSingleTreeNode[num_actions];
        totValue = 0.0;
        amafResults = 0.0;
        squaredTotValue = 0.0;
        this.childIdx = childIdx;
        if(parent != null) {
            m_depth = parent.m_depth + 1;
            this.rootStateHeuristic = sh;
        }
        else
            m_depth = 0;
    }

    void setRootGameState(GameState gs)
    {
        this.rootState = gs;

        if (params.heuristic_method == params.CUSTOM_HEURISTIC)
            this.rootStateHeuristic = new CustomHeuristic(gs);
        else if (params.heuristic_method == params.ADVANCED_HEURISTIC) // New method: combined heuristics
            this.rootStateHeuristic = new AdvancedHeuristic(gs, m_rnd);
        else if(params.heuristic_method == params.CMHeuristic)
            this.rootStateHeuristic = new CMHeuristic(gs);
    }


    void mctsSearch(ElapsedCpuTimer elapsedTimer) {
        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        int numIters = 0;

        int remainingLimit = 5;
        boolean stop = false;

        while(!stop){
            GameState state = rootState.copy();
            //this.boardCopy = state.getBoard();

            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            //CustomSingleTreeNode selected = treePolicy(state);
            //CustomSingleTreeNode selected = FPU_Selection(state);
            CustomSingleTreeNode selected = treePolicy(state);

            double delta = selected.rollOut(state);

            //CustomSingleTreeNode selected = pessimisticTreeSearch(state);
            //double delta = pessimisticRollout(state);
            backUp(selected, delta, alternativeNodes);
            //backUp(selected, delta, alternativeNodes); //RAVE


            //Stopping condition
            if(params.stop_type == params.STOP_TIME) {
                numIters++;
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis()) ;
                avgTimeTaken  = acumTimeTaken/numIters;
                remaining = elapsedTimer.remainingTimeMillis();
                stop = remaining <= 2 * avgTimeTaken || remaining <= remainingLimit;
            }else if(params.stop_type == params.STOP_ITERATIONS) {
                numIters++;
                stop = numIters >= params.num_iterations;
            }else if(params.stop_type == params.STOP_FMCALLS)
            {
                fmCallsCount+=params.rollout_depth;
                stop = (fmCallsCount + params.rollout_depth) > params.num_fmcalls;
            }
        }
        //System.out.println(" ITERS " + numIters);
    }

    private CustomSingleTreeNode treePolicy(GameState state) {

        CustomSingleTreeNode cur = this;

        while (!state.isTerminal() && cur.m_depth < params.rollout_depth)
        {
            if (cur.notFullyExpanded()) {
                return cur.expand(state);

            } else {
                //cur = cur.uct(state);
                //cur = cur.uct_tuned(state);
                cur = cur.uctRave(state);
            }
        }

        return cur;
    }

    private CustomSingleTreeNode FPU_Selection(GameState state){
        CustomSingleTreeNode cur = this;

        while (!state.isTerminal() && cur.m_depth < params.rollout_depth) {
            double best = -Double.MAX_VALUE;
            int chosenIdx = -1;
            for(int i = 0; i < num_actions; i++){
                double value = cur.children[i] == null ? params.fpu_value : uct_tuned(cur.children[i]);
                if (value > best){
                    best = value;
                    chosenIdx = i;
                }
            }

            if (cur.children[chosenIdx] == null){
                return cur.expand(state);
            }
            roll(state, actions[chosenIdx]);
            cur = cur.children[chosenIdx];
        }

        return cur;
    }

    private CustomSingleTreeNode expand(GameState state) {

        int bestAction = 0;
        double bestValue = -1;

        for (int i = 0; i < children.length; i++) {
            double x = m_rnd.nextDouble();
            if (x > bestValue && children[i] == null) {
                bestAction = i;
                bestValue = x;
            }
        }

        //Roll the state
        roll(state, actions[bestAction]);

        CustomSingleTreeNode tn = new CustomSingleTreeNode(params,this, bestAction,this.m_rnd,num_actions,
                actions, fmCallsCount, rootStateHeuristic);
        children[bestAction] = tn;
        return tn;
    }

    private void roll(GameState gs, Types.ACTIONS act)
    {
        //Simple, all random first, then my position.
        int nPlayers = 4;
        Types.ACTIONS[] actionsAll = new Types.ACTIONS[4];
        int playerId = gs.getPlayerId() - Types.TILETYPE.AGENT0.getKey();

        for(int i = 0; i < nPlayers; ++i)
        {
            if(playerId == i)
            {
                actionsAll[i] = act;
            }else {
                //int actionIdx = m_rnd.nextInt(gs.nActions());
                int actionIdx = opponentModelling(i);
                //System.out.println(actionIdx);
                actionsAll[i] = Types.ACTIONS.all().get(actionIdx);
            }
        }

        //gs = gs.copy();
        gs.next(actionsAll);
        // update board for new gs
        //this.boardCopy = CustomUtils.updateBoard(gs, this.boardCopy);

    }

    private double uct_tuned(CustomSingleTreeNode child){
        double bestValue = -Double.MAX_VALUE;
        double hvVal = child.totValue;
        double childValue =  hvVal / (child.nVisits + params.epsilon);
        double nVisits = child.nVisits + params.epsilon;
        childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

        double v = ((child.squaredTotValue / nVisits) - Math.pow(child.totValue / nVisits, 2))
                + Math.sqrt(2 * Math.log(this.nVisits + 1) / nVisits);

        //System.out.println(v);
        //System.out.println(((child.squaredTotValue / nVisits) - Math.pow(child.totValue / nVisits, 2)));
        double uctValue = childValue +
                params.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + params.epsilon)) * Math.min(.25, v);

        uctValue = Utils.noise(uctValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly



        return uctValue;
    }

    private CustomSingleTreeNode uctRave(GameState state) {
        CustomSingleTreeNode selected = null;
        double bestValue = -Double.MAX_VALUE;
        alternativeNodes = new ArrayList<>();
        for (CustomSingleTreeNode child : this.children)
        {
            alternativeNodes.add(child);
            double amafResult = child.amafResults;
            amafVisits = child.amafVisits;
            double amafScore = amafResult / (child.amafVisits + params.epsilon);
            double V = 10;
            double alpha = Math.max(0, (V - this.nVisits) / V);

            double hvVal = child.totValue;
            double childValue =  hvVal / (child.nVisits + params.epsilon);

            double qAmafVal = (alpha * amafScore) + ((1-alpha) * childValue);

            qAmafVal = Utils.normalise(qAmafVal, bounds[0], bounds[1]);  //childvalue



            double uctValue = qAmafVal +  ///childValue
                    params.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + params.epsilon));

            uctValue = Utils.noise(uctValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly

            // small sampleRandom numbers: break ties in unexpanded nodes
            if (uctValue > bestValue) {
                selected = child;
                bestValue = uctValue;
            }
            alternativeNodes.remove(selected);
        }
        if (selected == null)
        {
            throw new RuntimeException("Warning! returning null: " + bestValue + " : " + this.children.length + " " +
                    + bounds[0] + " " + bounds[1]);
        }

        //Roll the state:
        roll(state, actions[selected.childIdx]);
        return selected;
    }

    private CustomSingleTreeNode uct(GameState state) {
        CustomSingleTreeNode selected = null;
        double bestValue = -Double.MAX_VALUE;
        for (CustomSingleTreeNode child : this.children)
        {
            double hvVal = child.totValue;
            double childValue =  hvVal / (child.nVisits + params.epsilon);

            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue +
                    params.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + params.epsilon));

            uctValue = Utils.noise(uctValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly

            // Apply Progressive Bias
            if (params.isProgressive){
                uctValue += child.heuristicValue / (child.nVisits+1);
            }

            // small sampleRandom numbers: break ties in unexpanded nodes
            if (uctValue > bestValue) {
                selected = child;
                bestValue = uctValue;
            }
        }
        if (selected == null)
        {
            throw new RuntimeException("Warning! returning null: " + bestValue + " : " + this.children.length + " " +
                    + bounds[0] + " " + bounds[1]);
        }

        //Roll the state:
        roll(state, actions[selected.childIdx]);

        return selected;
    }

    private double rollOut(GameState state)
    {
        int thisDepth = this.m_depth;

        while (!finishRollout(state,thisDepth)) {
            int action = safeRandomAction(state);
            //int action = selectionMAST();
            roll(state, actions[action]);

            //roll(state, oslaAction(state));
            thisDepth++;
        }

        double heuristicValue = rootStateHeuristic.evaluateState(state);
        this.heuristicValue = heuristicValue;
        if (params.decaying){
            heuristicValue *= Math.pow(params.discount_factor, this.m_depth);
        }

        return heuristicValue;
    }

    private Types.ACTIONS oslaAction(GameState state){
        ArrayList<Types.ACTIONS> actionsList = Types.ACTIONS.all();
        double maxQ = Double.NEGATIVE_INFINITY;
        Types.ACTIONS bestAction = null;

        int num_actions = 3;
        //int actionsLength = actionsList.size();
        for (int i = 0; i < num_actions; i++) {
            int nAction = m_rnd.nextInt(actionsList.size());
            Types.ACTIONS act = actionsList.get(nAction);
            GameState gsCopy = state.copy();
            roll(gsCopy, act);
            double valState = rootStateHeuristic.evaluateState(gsCopy);

            //System.out.println(valState);
            double Q = Utils.noise(valState, params.epsilon, this.m_rnd.nextDouble());

            //System.out.println("Action:" + action + " score:" + Q);
            if (Q > maxQ) {
                maxQ = Q;
                bestAction = act;
            }

            actionsList.remove(nAction);
        }

        return bestAction;

    }

    private int safeRandomAction(GameState state)
    {
        Types.TILETYPE[][] board = state.getBoard();
        //Types.TILETYPE[][] board = this.boardCopy;
        //System.out.println(this.boardCopy);
        ArrayList<Types.ACTIONS> actionsToTry = Types.ACTIONS.all();
        int width = board.length;
        int height = board[0].length;

        while(actionsToTry.size() > 0) {

            int nAction = m_rnd.nextInt(actionsToTry.size());
            Types.ACTIONS act = actionsToTry.get(nAction);
            Vector2d dir = act.getDirection().toVec();

            Vector2d pos = state.getPosition();
            int x = pos.x + dir.x;
            int y = pos.y + dir.y;

            if (x >= 0 && x < width && y >= 0 && y < height) {
                if (board[y][x] != Types.TILETYPE.FLAMES && board[y][x] != Types.TILETYPE.WOOD && board[y][x] != Types.TILETYPE.RIGID)
                {
                    return nAction;
                }
            }
            actionsToTry.remove(nAction);
        }

        //return m_rnd.nextInt(num_actions);

        // Plant bomb
        return 3;
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean finishRollout(GameState rollerState, int depth)
    {
        if (depth >= params.rollout_depth)      //rollout end condition.
            return true;

        if (rollerState.isTerminal())               //end of game
            return true;

        return false;
    }

    private void backUp(CustomSingleTreeNode node, double result, ArrayList<CustomSingleTreeNode> alternativeNodes)
    {
        CustomSingleTreeNode n = node;
        while(n != null)
        {
            n.nVisits++;
            n.totValue += result;
            n.squaredTotValue += Math.pow(result, 2);
            if (alternativeNodes != null) {
                for (CustomSingleTreeNode alternativeNode : alternativeNodes) {
                    if (alternativeNode.childIdx == n.childIdx) {
                        alternativeNode.amafVisits++;
                        alternativeNode.amafResults += result;
                    }
                }
            }
            if (result < n.bounds[0]) {
                n.bounds[0] = result;
            }
            if (result > n.bounds[1]) {
                n.bounds[1] = result;
            }

            calculateMAST(result, n.childIdx);
            n = n.parent;
        }
    }

    private void calculateMAST(double result, int actIdx){
        if (actIdx == -1){
            return;
        }

        if(!params.MAST_Value.containsKey(actIdx)){
            params.MAST_Value.put(actIdx, 0.0);
            params.MAST_Count.put(actIdx, 0);
        }

        params.MAST_Value.put(actIdx, params.MAST_Value.get(actIdx) + result);
        params.MAST_Count.put(actIdx, params.MAST_Count.get(actIdx) + 1);
    }

    public int selectionMAST(){
        double[][] values = new double[num_actions][2];
        //int bestAction = 0;
        //double bestValue = -Double.MAX_VALUE;


        for(int i = 0; i < num_actions; i++){
            double value = 0.0;
            if(params.MAST_Value.containsKey(i)){
                value = params.MAST_Value.get(i) / params.MAST_Count.get(i);
            }
            values[i] = new double[]{value, i};
        }

        Arrays.sort(values, (x, y) -> Double.compare(x[0], y[0]));

        return (int) values[num_actions-1 -m_rnd.nextInt(3)][1];
    }

    int mostVisitedAction() {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;
        boolean allEqual = true;
        double first = -1;

        for (int i=0; i<children.length; i++) {

            if(children[i] != null)
            {
                if(first == -1)
                    first = children[i].nVisits;
                else if(first != children[i].nVisits)
                {
                    allEqual = false;
                }

                double childValue = children[i].nVisits;
                childValue = Utils.noise(childValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1)
        {
            selected = 0;
        }else if(allEqual)
        {
            //If all are equal, we opt to choose for the one with the best Q.
            selected = bestAction();
        }

        return selected;
    }

    private int bestAction()
    {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;

        for (int i=0; i<children.length; i++) {

            if(children[i] != null) {
                double childValue = children[i].totValue / (children[i].nVisits + params.epsilon);
                childValue = Utils.noise(childValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1)
        {
            System.out.println("Unexpected selection!");
            selected = 0;
        }

        return selected;
    }


    private boolean notFullyExpanded() {
        for (CustomSingleTreeNode tn : children) {
            if (tn == null) {
                return true;
            }
        }

        return false;
    }

    private int opponentModelling(int playerIdx){
        // storing only previous 20 moves
        int rnd = m_rnd.nextInt(20);
        return params.opponentMoves[playerIdx][this.m_depth + params.turnNum][rnd];
    }

    /*
    private boolean notExpanded(CustomSingleTreeNode cur){
        for (CustomSingleTreeNode tn : cur.children){
            if (tn != null){
                return false;
            }
        }
        return true;
    }

    private CustomSingleTreeNode pessimisticTreeSearch(GameState state){
        CustomSingleTreeNode cur = this;

        while (!state.isTerminal() && cur.m_depth < params.pessimism_depth) {
            if (cur.notFullyExpanded()) {
                return cur.expand(state);

            } else {
                cur = cur.uct(state);
            }
        }

        return cur;

    }

    private double pessimisticRollout(GameState state){
        //int thisDepth = this.m_depth+1;
        for(int i = 0; i < agentScores.length; i++){
            GameState stateCopy = state.copy();
            this.pessimismBoard = new int[11][11][10];
            for(int j = 0; j < params.determinism_length; j++) {
                if(params.pessimism_level > i){
                    pessimisticSimulation(stateCopy, i, j);
                }
                roll(stateCopy, actions[safeRandomAction(state)]);
            }
            agentScores[i] = pessimisticEvaluation();
        }

        return agentScores[0] / (agentScores[1] * agentScores[2] * agentScores[3]);
    }

    private void pessimisticSimulation(GameState state, int agentID, int timeStep){
        Types.TILETYPE[][] board = state.getBoard();
        for(int i = 0; i < 11; i++){
            for(int j = 0; j < 11; j++){
                switch(board[i][j]){
                    //case():
                }
            }
        }
    }

    private double pessimisticEvaluation(){

        return 0.0;
    }
    */
}
