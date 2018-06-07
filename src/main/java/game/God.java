package game;

import java.util.TreeMap;
import game.GodHelper.*;
import scala.Option;

public class God {

    private int playerNum;          // how many people to play the game
    private Player[] allPlayers;    // preserve the state of players
    private boolean humanWin;       // whether human team wins
    private boolean zombieWin;      // whether zombie team wins
    private Map map;                // map of the game
    private String[] heroList = {"0"};
    private UserInfo[] allUserInfo;
    private int[] cardHeap;
    private int[] availableFireTarget;
    private int[] availableMoveDirection;

    private String[] heroChoices;
    private int[] teamResult;
    private int[] decisionChoices;
    private int[] seenCardChoices;
    private int[] gambleChoices;
    private int[] cardNumList;
    private int[] playerWinList;
    private int[] energyList;
    private int[] healthPointList;
    private int[] locationList;
    private int[] elementList;
    private int[] teamList;
    enum GameState{ INIT,MAINGAME,END }
    private GameState gameState = GameState.INIT;
    private int[] playerState;
    enum PhaseState{ PREPARE, GAMBLE, ACTION }
    private PhaseState phaseState = PhaseState.PREPARE;

    public String request(int sid,String msg) {
        int playerIndex = 0;
        for(int i = 0;i < playerNum; i++){
            if(allPlayers[i].SID == sid) {
                playerIndex = i;
                break;
            }
        }
        String result = "{\"state\":\"Error\"}";
        switch(gameState) {
            case INIT:
                if(playerState[playerIndex] == 0) {
                    result = GodHelper.toInit(allUserInfo, "choose hero",playerIndex, heroList);
                    playerState[playerIndex] += 1;
                } else if(playerState[playerIndex] == 1) {
                    heroChoose(sid, msg);
                    gameState = GameState.MAINGAME;
                    playerState[playerIndex] = 0;
                    result = GodHelper.toChooseHero("start game", heroChoices, teamResult);
                }
                break;
            case MAINGAME:
                switch(phaseState){
                    case PREPARE:
                        if(playerState[playerIndex] == 0){
                            GambleChecker.cardDistribute(cardHeap,allPlayers[playerIndex],4);
                            int[] playerHandCard = new int[allPlayers[playerIndex].handCardsNum];
                            boolean isSeenCard = true;
                            for(int i = 0;i < allPlayers[playerIndex].handCardsNum;i++) {
                                playerHandCard[i] = allPlayers[playerIndex].handCards[i];
                                if((allPlayers[playerIndex].handCards[i] == 1)
                                        |(allPlayers[playerIndex].handCards[i] == 2)
                                        |(allPlayers[playerIndex].handCards[i] == 3))
                                    isSeenCard = false;
                            }
                            if(isSeenCard)
                                allPlayers[playerIndex].isSeenCard = true;
                            for(int i = 0; i < playerNum;i++){
                                if(MapChecker.distance(map.units[allPlayers[playerIndex].preLoc],map.units[allPlayers[i].preLoc]) < allPlayers[playerIndex].range)
                                    availableFireTarget[i] = 1;
                            }
                            toDirection(playerIndex);
                            result = GodHelper.toCardDistribute("choose strategy decision", playerHandCard,availableFireTarget,availableMoveDirection);
                            playerState[playerIndex] += 1;
                        }
                        else if(playerState[playerIndex] == 1){
                            MsgChooseDecision dec = GodHelper.getChooseDecision(msg);
                            // operation to the player's properties
                            if(dec.decision() == -1)
                                allPlayers[playerIndex].stratDecision = GambleChecker.DEPOSIT;
                            else{
                                allPlayers[playerIndex].stratDecision = allPlayers[playerIndex].handCards[dec.decision()];
                                GambleChecker.cardToHeap(cardHeap,dec.decision());
                                allPlayers[playerIndex].handCards[dec.decision()] = GambleChecker.NOTHING;
                                GambleChecker.cardSort(allPlayers[playerIndex].handCards);
                                allPlayers[playerIndex].handCardsNum -= 1;
                            }
                            decisionChoices[playerIndex] = allPlayers[playerIndex].stratDecision;
                            featureChoose(msg,allPlayers[playerIndex],playerIndex);
                            int[] playerHandCard = new int[allPlayers[playerIndex].handCardsNum];
                            for(int i = 0;i < allPlayers[playerIndex].handCardsNum;i++)
                                playerHandCard[i] = allPlayers[playerIndex].handCards[i];
                            //ends
                            result = GodHelper.toChooseDecision("choose seen card",playerHandCard);
                            playerState[playerIndex] += 1;
                        }
                        else if(playerState[playerIndex] == 2){
                            seenCard(playerIndex,msg,allPlayers[playerIndex]);
                            result = GodHelper.toSeenCard("GAMBLE:choose gamble",decisionChoices,seenCardChoices);
                            phaseState = PhaseState.GAMBLE;
                            playerState[playerIndex] = 0;
                        }
                        break;
                    case GAMBLE:
                        if(playerState[playerIndex] == 0) {
                            gambleChoose(msg,allPlayers[playerIndex],playerIndex);
                            playerState[playerIndex] += 1;
                            int[] playerHandCard = new int[allPlayers[playerIndex].handCardsNum];
                            for(int i = 0;i < allPlayers[playerIndex].handCardsNum;i++)
                                playerHandCard[i] = allPlayers[playerIndex].handCards[i];
                            result = GodHelper.toChooseGamble("win judge",playerHandCard);
                        }
                        else if(playerState[playerIndex] == 1){
                            winJudge();
                            for(int i = 0;i < playerNum;i++) {
                                if(allPlayers[i].isWin)
                                    playerWinList[i] = 1;
                            }
                            playerState[playerIndex] = 0;
                            phaseState = PhaseState.ACTION;
                            result = GodHelper.toWinJudge("ACTION: deposit account",gambleChoices,cardNumList,playerWinList);
                        }
                        break;
                    case ACTION:
                        if(playerState[playerIndex] == 0){
                            depositAccount();
                            result = GodHelper.toDepositAccount("skills account",energyList);
                            playerState[playerIndex] += 1;
                        }
                        else if(playerState[playerIndex] == 1){
                            skillsAccount();
                            result = GodHelper.toSkillsAccount("fire account");
                            playerState[playerIndex] += 1;
                        }
                        else if(playerState[playerIndex] == 2){
                            fireAccount();
                            result = GodHelper.toFireAccount("move account",healthPointList);
                            playerState[playerIndex] += 1;
                        }
                        else if(playerState[playerIndex] == 3){
                            moveAccount();
                            result = GodHelper.toMoveAccount("element account",locationList);
                            playerState[playerIndex] += 1;
                        }
                        else if(playerState[playerIndex] == 4){
                            elemAccount();
                            result = GodHelper.toElemAccount("if human wins",elementList);
                            playerState[playerIndex] += 1;
                        }
                        else if(playerState[playerIndex] == 5){
                            humanVictory();
                            if(humanWin) {
                                result = GodHelper.toHumanVictory("Game Over, human wins");
                                playerState[playerIndex] = 0;
                                gameState = GameState.END;
                            }
                            else {
                                result = GodHelper.toHumanVictory("infection account");
                                playerState[playerIndex] += 1;
                            }
                        }
                        else if(playerState[playerIndex] == 6){
                            infectionAccount();
                            if(zombieWin){
                                result = GodHelper.toInfectionAccount("Game Over, zombie wins",teamList);
                                playerState[playerIndex] = 0;
                                gameState = GameState.END;
                            }
                            else {
                                result = GodHelper.toInfectionAccount("desert account", teamList);
                                playerState[playerIndex] += 1;
                            }
                        }
                        else if(playerState[playerIndex] == 7){
                            desertAccount(playerIndex,msg);
                            int[] playerHandCard = new int[allPlayers[playerIndex].handCardsNum];
                            for(int i = 0;i < allPlayers[playerIndex].handCardsNum;i++)
                                playerHandCard[i] = allPlayers[playerIndex].handCards[i];
                            result = GodHelper.toDesertAccount("PREPARE: choose decision",allPlayers[playerIndex].energy,playerHandCard);
                            phaseState = PhaseState.PREPARE;
                            playerState[playerIndex] = 0;
                            // reset all temp variables
                            for(int i = 0;i < playerNum;i++){
                                availableFireTarget[i] = 0;
                                decisionChoices[i] = 7;
                                seenCardChoices[i] = 0;
                                gambleChoices[i] = GambleChecker.PAPER;
                                cardNumList[i] = 0;
                                playerWinList[i] = 0;
                            }
                        }
                        break;
                }
                break;
            case END:
                break;
        }

        // for debug
        if(result.equals("{\"state\":\"Error\"}")){
            System.out.printf("%d %s %s\n",sid,msg,gameState.toString());
        }

        return result;
    }

    /**
     * units code
     * wait all players to send message
     */
    private final TreeMap<GameState,Integer> wait_map = new TreeMap<GameState, Integer>();
    private void waitAllPlayers(){
        synchronized (wait_map){
            Integer counter = wait_map.get(gameState);
            if(counter==null) counter = 0;
            counter++;
            wait_map.put(gameState,counter);
            if(counter < playerNum){
                do{
                    try{
                        wait_map.wait();
                    }catch (Exception ex){
                        ex.printStackTrace();
                    }
                    counter = wait_map.get(gameState);
                }while (counter < playerNum);
            } else {
                wait_map.notifyAll();
            }
        }
    }

    // functions for INIT stage
    private Integer choice_count = 0;
    private void heroChoose(int sid,String msg){
        int playerIndex;
        String heroChoice;
        MsgChooseHero choose = GodHelper.getChooseHero(msg);
        synchronized (this){
            choice_count += 1;
            for( playerIndex = 0;playerIndex < playerNum;playerIndex++){
                if(allPlayers[playerIndex].SID == sid) {
                    allPlayers[playerIndex].chara = choose.hero();
                    heroChoices[playerIndex] = choose.hero();
                }
            }
            if(choice_count < playerNum){
                while (choice_count < playerNum){
                    try {
                        this.wait();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            } else {
                teamDivide();
                mapInit();
                GambleChecker.cardHeapInit(cardHeap,playerNum);
                GambleChecker.cardHeapStir(cardHeap);
                this.notifyAll();
            }
        }
    }
    private void teamDivide(){
        int zombie = (int)( Math.random() * playerNum);
        allPlayers[zombie].team = Player.ZOMBIE;
        teamResult[zombie] = Player.ZOMBIE;
        for(int i = 0;i < allPlayers.length;i++){
            if(i != zombie) {
                allPlayers[i].team = Player.HUMAN;
                teamResult[i] = Player.HUMAN;
            }
        }
    }
    private void mapInit(){
        for(int i = 0;i < playerNum;i++){
            if(allPlayers[i].team == Player.HUMAN)
                allPlayers[i].preLoc = map.fighter_init;
            else if(allPlayers[i].team == Player.ZOMBIE)
                allPlayers[i].preLoc = map.poisoner_init;
        }
    }

    // functions for GAMBLE stage
    private void featureChoose(String msg,Player playerMain,int playerIndex){
        MsgChooseDecision decisionFeature = GodHelper.getChooseDecision(msg);
        int decision = playerMain.stratDecision;
        int direction = toLoc(playerIndex,decisionFeature.moveDirection());
        if(decision == GambleChecker.MOVE) {
            playerMain.moveDirection = decisionFeature.moveDirection();
            playerMain.energyConsume = Math.min(playerMain.energy,playerMain.mot);
        }
        else if(decision == GambleChecker.FIRE) {
            playerMain.fireTarget = decisionFeature.fireTarget();
            playerMain.energyConsume = Math.min(playerMain.energy,playerMain.firePow);
        }
        else if(decision == GambleChecker.DEPOSIT){
            playerMain.energyConsume = 0;
        }
    }
    private Integer seen_card_count = 0;
    private void seenCard(int playerIndex,String msg,Player playerMain){
        MsgSeenCard msgSeenCard = GodHelper.getSeenCard(msg);
        synchronized (this){
            seen_card_count += 1;
            if((playerMain.isSeenCard)|(msgSeenCard.seenCard() != 0)){
                playerMain.gamble = msgSeenCard.seenCard();
                playerMain.gambleNum = 1;
                playerMain.isSeenCard = true;
                seenCardChoices[playerIndex] = msgSeenCard.seenCard();
            }
            if(seen_card_count < playerNum){
                while(seen_card_count < playerNum){
                    try{
                        this.wait();
                    }catch (Exception ex){
                        ex.printStackTrace();
                    }
                }
            }else {
                this.notifyAll();
            }
        }
    }
    private void gambleChoose(String msg, Player playerMain,int playerIndex){
        MsgChooseGamble msgChooseGamble = GodHelper.getChooseGamble(msg);
        int[] gambleChoose = new int[msgChooseGamble.gambleCard().length];
        for(int i = 0;i < msgChooseGamble.gambleCard().length;i++)
            gambleChoose[i] = msgChooseGamble.gambleCard()[i];
        if(!playerMain.isSeenCard) {
            playerMain.gamble = playerMain.handCards[ gambleChoose[0] ];
            playerMain.gambleNum = gambleChoose.length;
            for(int i = 0;i < playerMain.gambleNum;i++) {
                GambleChecker.cardToHeap(cardHeap,playerMain.handCards[ playerMain.handCards[gambleChoose[i]] ]);
                playerMain.handCards[gambleChoose[i]] = GambleChecker.NOTHING;
                GambleChecker.cardSort(playerMain.handCards);
                playerMain.handCardsNum -= 1;
            }
        }
        gambleChoices[playerIndex] = playerMain.gamble;
        cardNumList[playerIndex] = playerMain.gambleNum;
    }
    private Integer gamble_count = 0;
    private void winJudge(){
        synchronized (this){
            gamble_count += 1;
            if(gamble_count < playerNum){
                while(gamble_count < playerNum){
                    try{
                        this.wait();
                    }catch (Exception ex){
                        ex.printStackTrace();
                    }
                }
            }else{
                GambleChecker.winJudge(playerNum,allPlayers);
                this.notifyAll();
            }
        }
    }
    /* transform between direction and location
     * dirty functions
     */
    private void toDirection(int playerIndex){
        if(allPlayers[playerIndex].preLoc == 7) {
            availableMoveDirection[0] = 0;
            availableMoveDirection[1] = 1;
            availableMoveDirection[2] = 0;
            availableMoveDirection[3] = 0;
            availableMoveDirection[4] = 1;
            availableMoveDirection[5] = 0;
            availableMoveDirection[6] = 1;
            availableMoveDirection[7] = 0;
        }
        else if(allPlayers[playerIndex].preLoc == 15){
            availableMoveDirection[0] = 1;
            availableMoveDirection[1] = 0;
            availableMoveDirection[2] = 1;
            availableMoveDirection[3] = 0;
            availableMoveDirection[4] = 0;
            availableMoveDirection[5] = 0;
            availableMoveDirection[6] = 0;
            availableMoveDirection[7] = 0;
        }
        else if(allPlayers[playerIndex].preLoc == 0){
            availableMoveDirection[0] = 0;
            availableMoveDirection[1] = 0;
            availableMoveDirection[2] = 0;
            availableMoveDirection[3] = 0;
            availableMoveDirection[4] = 0;
            availableMoveDirection[5] = 1;
            availableMoveDirection[6] = 0;
            availableMoveDirection[7] = 0;
        }
        else{
            availableMoveDirection[0] = 0;
            availableMoveDirection[1] = 1;
            availableMoveDirection[2] = 0;
            availableMoveDirection[3] = 0;
            availableMoveDirection[4] = 0;
            availableMoveDirection[5] = 1;
            availableMoveDirection[6] = 0;
            availableMoveDirection[7] = 0;
        }
    }
    private int toLoc(int playerIndex,int direction){
        if(allPlayers[playerIndex].preLoc == 7){
            if(direction == 5)
                return 6;
            else if(direction == 0)
                return 8;
            else
                return 22;
        }
        else if(allPlayers[playerIndex].preLoc == 15){
            if(direction == 6)
                return 14;
            else
                return 16;
        }
        else if(allPlayers[playerIndex].preLoc == 0){
            return 1;
        }
        else {
            if(direction == 1)
                return allPlayers[playerIndex].preLoc++;
            else
                return allPlayers[playerIndex].preLoc--;
        }
    }

    // functions for ACTION stage
    private void depositAccount(){
        for(int i = 0;i < playerNum;i++) {
            PlayerChecker.energyConsume(allPlayers[i], allPlayers[i].energyConsume);
            if (allPlayers[i].isWin)
                PlayerChecker.energyAcq(allPlayers[i], allPlayers[i].gambleNum);
            energyList[i] = allPlayers[i].energy;
        }
    }
    private void skillsAccount(){}     //TODO:skills
    private void fireAccount(){
        for(int i = 0;i < playerNum;i++) {
            if (allPlayers[i].stratDecision == GambleChecker.FIRE)
                PlayerChecker.fire(map, allPlayers[i], allPlayers[allPlayers[i].fireTarget]);
            healthPointList[i] = allPlayers[i].healthPoint;
        }
    }
    private void moveAccount(){
        for(int i = 0;i < playerNum;i ++) {
            if (allPlayers[i].stratDecision == GambleChecker.MOVE)
                allPlayers[i].preLoc = MapChecker.tryMove(map, allPlayers[i].preLoc, allPlayers[i].moveDirection, allPlayers[i].energyConsume);
            locationList[i] = allPlayers[i].preLoc;
        }
    }
    private void elemAccount(){
        for(int i = 0;i < playerNum;i++){
            if (map.units[allPlayers[i].preLoc].status == 2)
            {
                allPlayers[i].hasElem = true;
                elementList[i] = 1;
            }
            //TODO: a player got element, he should obtain scores
        }
    }
    private void humanVictory(){
        for(int i = 0;i < playerNum;i++) {
            if (allPlayers[i].preLoc == map.fighter_evacuate) {
                humanWin = true;
                //TODO: a player arrived at evacuate spot, he should obtain scores
            }
        }
    }
    private void infectionAccount(){
        for(int i = 0;i < playerNum;i++) {
            for(int j = 0;j < playerNum;j++) {
                PlayerChecker.infection(map, allPlayers[i], allPlayers[j]);
                //TODO: one player infected another,he should obtain scores
            }
        }
        for(int i = 0;i < playerNum;i++)
            teamList[i] = allPlayers[i].team;
        Boolean flag = true;
        for(int i = 0;i < playerNum;i++){
            if(allPlayers[i].team == Player.HUMAN)
                flag = false;
        }
        if(flag)
            zombieWin = true;
    }
    private int desert_count = 0;
    private void desertAccount(int playerIndex,String msg){
        MsgDesertAccount msgDesertAccount = GodHelper.getDesertAccount(msg);
        synchronized (this){
            desert_count += 1;
            GambleChecker.cardDesert(allPlayers[playerIndex],cardHeap,msgDesertAccount.desertCardList());
            if(allPlayers[playerIndex].energy > allPlayers[playerIndex].healthPoint)
                allPlayers[playerIndex].energy = allPlayers[playerIndex].healthPoint;
            if(desert_count < playerNum){
                while(desert_count < playerNum){
                    try{
                        this.wait();
                    }catch(Exception ex){
                        ex.printStackTrace();
                    }
                }
            }else{
                this.notifyAll();
            }
        }
    }

    public void initialPlayer(int[] playerSID)  {
        playerNum = playerSID.length;
        allPlayers = new Player[playerNum];
        allUserInfo = new UserInfo[playerNum];
        playerState = new int[playerNum];
        map = new Map("map/1.map");

        heroChoices = new String[playerNum];
        teamResult = new int[playerNum];
        decisionChoices = new int[playerNum];
        seenCardChoices = new int[playerNum];
        cardHeap = new int[40 * playerNum];
        gambleChoices = new int[playerNum];
        cardNumList = new int[playerNum];
        playerWinList = new int[playerNum];
        energyList = new int[playerNum];
        healthPointList = new int[playerNum];
        locationList = new int[playerNum];
        elementList = new int[playerNum];
        teamList = new int[playerNum];
        availableFireTarget = new int[playerNum];
        availableMoveDirection = new int[8];

        for(int i = 0;i < playerNum;i++) {
            allPlayers[i] = new Player();
            allPlayers[i].SID = playerSID[i];
            allPlayers[i].handCardsNum = 0;
            allPlayers[i].handCards = new int[allPlayers[i].healthPoint + 4];
            playerState[i] = 0;
            Option<UserModel.User> user = UserController.getProfile(playerSID[i]);
            if(user.isEmpty()) allPlayers[i].user_info = GodHelper.ghostUser();
            else allPlayers[i].user_info = user.get();
            allUserInfo[i] = new UserInfo(i,allPlayers[i].user_info.nickname());
        }
    }

}