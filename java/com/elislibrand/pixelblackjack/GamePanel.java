package com.elislibrand.pixelblackjack;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

public class GamePanel extends JPanel implements Runnable
{
    private static final long serialVersionUID = 1L;

    private Thread thread;

    private GameState gameState;
    private SplitStage splitStage;

    private final Animator animator = new Animator();
    private final AudioManager audioManager = new AudioManager();
    private final TextManager textManager = new TextManager();

    private final List<Graphic> graphics = new ArrayList<Graphic>();
    private List<VisualChip> visualChips;

    private final Player player = new Player();
    private final Hand dealerHand = new Hand();

    private final int scale = Screen.SCALE;

    private final Dimension screenSize = new Dimension(Screen.WIDTH, Screen.HEIGHT);
    private final Dimension cardSize = new Dimension(33 * scale, 49 * scale);
    private final Dimension cardShadowSize = new Dimension(35 * scale, 51 * scale);
    private final Dimension chipSize = new Dimension(21 * scale, 21 * scale);
    private final Dimension chipInChipTraySize = new Dimension(21 * scale, 2 * scale);
    private final Dimension chipShadowSize = new Dimension(23 * scale, 23 * scale);
    private final Dimension betSquareSize = new Dimension(44 * scale, 58 * scale);
    private final Dimension dealerTraySize = new Dimension(190 * scale, 88 * scale);
    private final Dimension infoTextAreaSize = new Dimension(95 * scale, 19 * scale);
    private final Dimension arrowSize = new Dimension(5 * scale, 5 * scale);

    private final Point playerCardStartingPos = new Point((screenSize.width / 2) - ((int)((cardSize.width / scale) / 2) * scale) - (1 * scale), // There is room for a number under the card, displaying the hand's value
                                                          screenSize.height - cardSize.height - betSquareSize.height - (31 * scale));           // (3 * scale) pixels margin top and bottom from the number ?
    private final Point dealerCardStartingPos = new Point((screenSize.width / 2) + (2 * scale), dealerTraySize.height + (31 * scale));
    private final Point betSquarePos = new Point((screenSize.width / 2) - (betSquareSize.width / 2), screenSize.height - betSquareSize.height - (20 * scale));
    private final Point dealerTrayPos = new Point((screenSize.width / 2) - (dealerTraySize.width / 2), (20 * scale));
    private final Point infoTextAreaPos = new Point(screenSize.width - infoTextAreaSize.width - (46 * scale), (20 * scale));
    private final Point chipStartingPos = new Point((screenSize.width / 2) - (chipSize.width / 2) - (6 * scale),
                                                    screenSize.height - (betSquareSize.height / 2) - (chipSize.height / 2) - ((1 * scale) - 1) + (2 * scale)); // Top-left
    private final Point chipShadowStartingPos = new Point(chipStartingPos.x - scale, chipStartingPos.y - scale);
    private final Point cardHolderPos = new Point(screenSize.width - cardSize.width, -cardSize.height);
    private final Point playerCardOffset = new Point(8 * scale, -(10 * scale));
    private final Point dealerCardOffset = new Point(-(cardSize.width + (4 * scale)), 0);
    private final Point chipSplitOffset = new Point((2 * cardSize.width) - (3 * scale), 0);
    private final Point chipDoubleDownOffset = new Point(chipSize.width + (3 * scale), 0);

    private final int minBet = 1;
    private final int maxBet = 100000;
    private final int numberOfDecks = 6;

    private final int betRowOffsetX = chipSize.width + (24 * scale);
    private final int betRowMarginX = (screenSize.width - (betRowOffsetX * (ChipValue.values().length - 1) + chipSize.width)) / 2;

    private final int splitOffsetX = (2 * cardSize.width) + (1 * scale);
    private final int splitMarginX = (screenSize.width - (splitOffsetX * (player.getMaxNumberOfHands() - 1) + cardSize.width)) / 2;
    
    private int cameraY = 0;
    private int indexOfFirstVisualCardInGraphics;

    private int splitDirection;
    private int splitIndexToMoveTo;
    private int movesNeeded;

    private boolean canClearBoard = false;
    private boolean debugMode = false;

    private Image cardFaceDown;
    private Image cardShadow;
    private Image chipShadow;
    private Image betSquare;
    private Image dealerTray;
    private Image infoTextArea;
    private Image chipInBetSquare;
    private Image arrowImage;

    //private final ChipTray chipTray = new ChipTray(player.getChips());

    private final Deck playingDeck = new Deck();
    private final Deck usedDeck = new Deck();
    
    private Graphic arrow;
    
    public GamePanel()
    {
        initializePanel();
    }

    private final void initializePanel()
    {
        System.out.println("\nScreen Resolution: " + screenSize.width + "x" + screenSize.height +
                           "\nScreen Refresh Rate: " + Screen.REFRESH_RATE +
                           "\nScreen Bit Depth: " + Screen.BIT_DEPTH);

        addKeyListener(new TAdapter());
        //setOpaque(false);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setBackground(new Color(48, 102, 60));
        setPreferredSize(new Dimension(Screen.WIDTH, Screen.HEIGHT));

        loadImages();
        initializeGame();
    }

    private final void loadImages()
    {
        cardFaceDown = new ImageIcon(getClass().getResource("/assets/cards/facedown.png")).getImage();
        cardShadow = new ImageIcon(getClass().getResource("/assets/cards/shadow.png")).getImage();
        chipShadow = new ImageIcon(getClass().getResource("/assets/chips/shadow.png")).getImage();
        betSquare = new ImageIcon(getClass().getResource("/assets/props/bet_square.png")).getImage();
        dealerTray = new ImageIcon(getClass().getResource("/assets/props/dealer_tray.png")).getImage();
        infoTextArea = new ImageIcon(getClass().getResource("/assets/props/info_text_area.png")).getImage();
        arrowImage = new ImageIcon(getClass().getResource("/assets/props/arrow.png")).getImage();
    }

    private final void initializeGame()
    {
        player.setCurrentHandIndex((player.getMaxNumberOfHands() / 2) - 1);

        player.setBet(minBet);

        createAllPlayerHands();
        activateHands();
        createPlayingDeck();
        createArrow();
        //sortChipTray();

        indexOfFirstVisualCardInGraphics = graphics.size();

        gameState = GameState.SHUFFLE_DECK;
    }

    private final void createAllPlayerHands()
    {
        for (int i = 0; i < player.getMaxNumberOfHands(); i++)
        {
            player.addHand(new Hand());
        }
    }

    private final void activateHands()
    {
        dealerHand.setActive(true);
        player.getHand(player.getCurrentHandIndex()).setActive(true);
    }

    private final void createPlayingDeck()
    {
        for (int i = 0; i < numberOfDecks; i++)
        {
            playingDeck.createFullDeck();
        }
    }

    private final void createArrow()
    {
        arrow = new Arrow(arrowImage, 0, 0, arrowSize.width, arrowSize.height, graphics.size());
        graphics.add(arrow);
    }

    private void placeBet()
    {
        audioManager.play(Audio.CHIPS_SINGLE_DROP);

        //chipInBetSquare = chipTray.getTopDownImage(player.getBet());
        
        player.placeBet();
        player.setPlacedBet(true);

        //sortChipTray(); //removeFromChipTray

        gameState = GameState.RECEIVE_CARDS;
    }

    private void receiveCards()
    {
        if (!animator.isPlaying())
        {
            if (graphics.size() == indexOfFirstVisualCardInGraphics)
            {
                drawCard(player.getHand(player.getCurrentHandIndex()), playerCardStartingPos, false, false);
            }
            else if (graphics.size() == indexOfFirstVisualCardInGraphics + 1)
            {
                drawCard(dealerHand, dealerCardStartingPos, true, false);
            }
            else if (graphics.size() == indexOfFirstVisualCardInGraphics + 2)
            {
                drawCard(player.getHand(player.getCurrentHandIndex()), getNextPlayerCardPosition(playerCardStartingPos, player.getCurrentHandIndex()), false, false);
            }
            else if (graphics.size() == indexOfFirstVisualCardInGraphics + 3)
            {
                drawCard(dealerHand, new Point(dealerCardStartingPos.x + dealerCardOffset.x, dealerCardStartingPos.y + dealerCardOffset.y), false, false);
            }
            else
            {
                checkForDealerBlackjack();
            }

            /* switch (graphics.size())
            {
                case indexOfFirstVisualCardInGraphics:
                    System.out.println(indexOfFirstVisualCardInGraphics);
                    
                    drawCard(player.getHand(player.getCurrentHandIndex()), playerCardStartingPos, false, false);
                    break;
                case 1:
                    drawCard(dealerHand, dealerCardStartingPos, true, false);
                    break;
                case 2:
                    drawCard(player.getHand(player.getCurrentHandIndex()), getNextPlayerCardPosition(playerCardStartingPos, player.getCurrentHandIndex()), false, false);
                    break;
                case 3:
                    drawCard(dealerHand, new Point(dealerCardStartingPos.x + dealerCardOffset.x, dealerCardStartingPos.y + dealerCardOffset.y), false, false);
                    break;
                case 4:
                    checkForDealerBlackjack();
                    break;
                default:
                    break;
            } */
        }
    }

    private Point getNextPlayerCardPosition(Point startingPos, int handIndex)
    {
        return new Point(startingPos.x + (playerCardOffset.x * player.getNumberOfCardsInHand(handIndex)),
                         startingPos.y + (playerCardOffset.y * player.getNumberOfCardsInHand(handIndex)));
    }

    private Point getNextSplitPlayerCardPosition(int handIndex)
    {
        return new Point(splitMarginX + (splitOffsetX * handIndex) + (playerCardOffset.x * player.getNumberOfCardsInHand(handIndex)),
                         playerCardStartingPos.y + (playerCardOffset.y * player.getNumberOfCardsInHand(handIndex)));
    }

    private Point getNextDealerCardPosition()
    {
        return new Point(dealerCardStartingPos.x + (dealerCardOffset.x * dealerHand.getNumberOfCards()),
                         dealerCardStartingPos.y + (dealerCardOffset.y * dealerHand.getNumberOfCards()));
    }

    private Point getCurrentHandVisualCardPosition()
    {
        return new Point(graphics.get(player.getHand(player.getCurrentHandIndex()).getGraphicsIndex(0)).getX(),
                         graphics.get(player.getHand(player.getCurrentHandIndex()).getGraphicsIndex(0)).getY());
    }

    private void drawCard(Hand destinationHand, Point destinationPos, boolean faceDown, boolean isRotated)
    {
        destinationHand.drawCardFromDeck(playingDeck);
        
        Card drawnCard = destinationHand.getCard(destinationHand.getNumberOfCards() - 1);
        Image cardImage = drawnCard.getImage();

        if (faceDown)
        {
            cardImage = cardFaceDown;
        }

        graphics.add(new VisualCard(cardImage, cardHolderPos.x, cardHolderPos.y, cardSize.width, cardSize.height, isRotated));
        destinationHand.addGraphicsIndex(graphics.size() - 1);

        animator.start(cardHolderPos, destinationPos, graphics.size() - 1);
        audioManager.play(Audio.CARD_DRAW);
    }

    private void checkForDealerBlackjack()
    {
        if (hasBlackjackDealer())
        {
            gameState = GameState.DEALER_DRAW;
        }
        else
        {
            gameState = GameState.PLAYER_CHOOSE;
        }
    }

    private boolean hasBlackjackDealer()
    {        
        if (dealerHand.getValueOfCards() == 21)
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    private void checkForPlayerBlackjack(int index)
    {
        player.checkForBlackjackInHand(index);

        if (player.isBlackjackInHand(index) || player.shouldAutoStandInHand(index))
        {
            if (player.isAnotherHand())
            {    
                goToNextHand();
            }
            else
            {
                gameState = GameState.DEALER_DRAW;
            }
        }
    }

    boolean canTakeCard = true;

    private void hit()
    {
        if (canTakeCard)
        {
            if (player.getNumberOfActiveHands() > 1)
            {
                drawCard(player.getHand(player.getCurrentHandIndex()), getNextSplitPlayerCardPosition(player.getCurrentHandIndex()), false, false);
            }
            else
            {
                drawCard(player.getHand(player.getCurrentHandIndex()), getNextPlayerCardPosition(playerCardStartingPos, player.getCurrentHandIndex()), false, false);
            }

            canTakeCard = false;
        }
        else
        {
            if (!animator.isPlaying)
            {
                checkForPlayerBust(player.getCurrentHandIndex());
                canTakeCard = true;
            }
        }
    }

    private void stand()
    {
        if (player.isAnotherHand())
        {    
            goToNextHand();
        }
        else
        {
            gameState = GameState.DEALER_DRAW;
        }
    }

    private void doubleDown()
    {
        if (canTakeCard)
        {
            if (canDoubleDown())
            {
                doubleBet();
                player.setDoubledDownToTrueInHand(player.getCurrentHandIndex());
                
                if (player.getNumberOfActiveHands() > 1)
                {
                    drawCard(player.getHand(player.getCurrentHandIndex()), getNextSplitPlayerCardPosition(player.getCurrentHandIndex()), false, true);
                }
                else
                {
                    drawCard(player.getHand(player.getCurrentHandIndex()), getNextPlayerCardPosition(playerCardStartingPos, player.getCurrentHandIndex()), false, true);
                }

                canTakeCard = false;
            }
            else
            {
                gameState = GameState.PLAYER_CHOOSE;
            }
        }
        else
        {
            if (!animator.isPlaying)
            {
                checkForPlayerBust(player.getCurrentHandIndex());
                canTakeCard = true;
            }
        }
    }

    private void split()
    {
        if (!animator.isPlaying())
        {
            switch (splitStage)
            {
                case INITIATE:
                    initiateSplit();

                    break;
                case MOVE_HANDS:
                    if (movesNeeded > 0)
                    {
                        shiftSplitCards();

                        movesNeeded--;
                        splitStage = splitStage.previous();
                    }

                    break;
                case MOVE_UPPER_VISUAL_CARD:
                    int upperVisualCardIndex = player.getHand(player.getCurrentHandIndex()).getGraphicsIndex(1);
                    moveSplitCard(player.getCurrentHandIndex() + splitDirection, upperVisualCardIndex);

                    if (player.getNumberOfActiveHands() > 2)
                    {
                        splitStage = splitStage.next();
                    }

                    break;
                case MOVE_LOWER_VISUAL_CARD:
                    int lowerVisualCardIndex = player.getHand(player.getCurrentHandIndex()).getGraphicsIndex(0);
                    moveSplitCard(player.getCurrentHandIndex(), lowerVisualCardIndex);

                    break;
                case SETUP_NEW_HANDS:
                    player.getHand(player.getCurrentHandIndex() + splitDirection).setActive(true);
                    player.getHand(player.getCurrentHandIndex()).moveLastCardToHand(player.getHand((player.getCurrentHandIndex() + splitDirection)));
                    player.getHand(player.getCurrentHandIndex()).moveLastGraphicsIndexToHand(player.getHand(player.getCurrentHandIndex() + splitDirection));

                    break;
                case DRAW_FIRST_CARD:
                    drawCardAfterSplit();

                    break;
                case DRAW_SECOND_CARD:
                    drawCardAfterSplit();
                    
                    break;
                case INCREMENT_CURRENT_HAND_INDEX:
                    if (splitDirection == 1)
                    {
                        player.incrementCurrentHandIndex();
                    }

                    break;
                case DISPLAY_ARROW:
                    Point position = ((Arrow)arrow).getPosition(getCurrentHandVisualCardPosition().x, getCurrentHandVisualCardPosition().y,
                                                                      cardSize.width,
                                                                      cardSize.height);

                    if (!((Arrow)arrow).isActive())
                    {
                        arrow.setX(position.x);
                        arrow.setY(position.y);
                
                        ((Arrow)arrow).setActive(true);
                    }
                    else if (splitDirection == 1)
                    {
                        animator.start(new Point(arrow.getX(), arrow.getY()), position, ((Arrow)arrow).getGraphicsIndex());
                    }

                    break;
                case CHANGE_GAME_STATE:
                    gameState = GameState.PLAYER_CHOOSE;
                    
                    break;
            }

            splitStage = splitStage.next();
        }
    }

    private void initiateSplit()
    {
        doubleBet();

        player.incrementNumberOfActiveHands();

        splitDirection = getSplitDirection();
        splitIndexToMoveTo = getSplitIndexToMoveTo();
        movesNeeded = Math.abs(player.getCurrentHandIndex() - splitIndexToMoveTo) - 1;
    }

    private int getSplitDirection()
    {
        if (player.getNumberOfActiveHands() <= 2)
        {
            return 1;
        }

        if (player.getCurrentHandIndex() > (player.getMaxNumberOfHands() / 2) - 1)
        {
            for (int i = player.getCurrentHandIndex() + 1; i < player.getMaxNumberOfHands(); i++) 
            {
                if (!player.getHand(i).isActive())
                {
                    return 1;
                }
            }

            return -1;
        }
        else
        {
            for (int i = player.getCurrentHandIndex() - 1; i >= 0; i--)
            {
                if (!player.getHand(i).isActive())
                {
                    return -1;
                }
            }

            return 1;
        }
    }

    private int getSplitIndexToMoveTo()
    {
        for (int i = player.getCurrentHandIndex() + splitDirection; shouldStopSearchingForSplitIndexToMoveTo(i); i += splitDirection)
        {
            if (!player.getHand(i).isActive())
            {
                return i;
            }
        }
        
        return -1;
    }

    private boolean shouldStopSearchingForSplitIndexToMoveTo(int index)
    {
        if (splitDirection == 1)
        {
            return index < player.getMaxNumberOfHands();
        }

        return index >= 0;
    }

    private void moveSplitCard(int handIndex, int graphicsIndex)
    {
        Graphic visualCard = graphics.get(graphicsIndex);

        Point startingPos = new Point(visualCard.getX(), visualCard.getY());
        Point destinationPos = new Point(splitMarginX + (splitOffsetX * handIndex), playerCardStartingPos.y);

        animator.start(startingPos, destinationPos, graphicsIndex);
        audioManager.play(Audio.CARD_SLIDE);
    }

    private void shiftSplitCards()
    {
        int handToShiftIndex = player.getCurrentHandIndex() + (movesNeeded * splitDirection);
            
        for (int i = 0; i < player.getHand(handToShiftIndex).getNumberOfCards(); i++) // Move all cards in the hand to be shifted
        {
            int graphicsIndex = player.getHand(handToShiftIndex).getGraphicsIndex(i);

            Point startingPos = new Point(graphics.get(graphicsIndex).getX(), graphics.get(graphicsIndex).getY());
            Point destinationPos = new Point(startingPos.x + (splitOffsetX * splitDirection), startingPos.y);

            animator.start(startingPos, destinationPos, graphicsIndex);
        }

        player.swapHands(handToShiftIndex, handToShiftIndex + splitDirection);
    }

    private void drawCardAfterSplit()
    {
        if ((splitDirection == 1 && splitStage == SplitStage.DRAW_FIRST_CARD) || (splitDirection == -1 && splitStage == SplitStage.DRAW_SECOND_CARD))
        {
            drawCard(player.getHand(player.getCurrentHandIndex() + splitDirection), getNextSplitPlayerCardPosition(player.getCurrentHandIndex() + splitDirection), false, false);
        }
        else
        {
            drawCard(player.getHand(player.getCurrentHandIndex()), getNextSplitPlayerCardPosition(player.getCurrentHandIndex()), false, false);
        }
    }

    private boolean canDoubleDown()
    {
        if (player.canDoubleBet() && player.canDoubleDownInHand(player.getCurrentHandIndex()))
        {
            return true;
        }
        
        return false;
    }

    private boolean canSplit()
    {
        if (player.canDoubleBet() && player.canSplitHand(player.getCurrentHandIndex()))
        {
            return true;
        }
        
        return false;
    }

    private void doubleBet()
    {
        player.doubleBet();
        
        //sortChipTray();
    }

    private void goToNextHand()
    {
        player.decrementCurrentHandIndex();
        
        Point destinationPos = ((Arrow)arrow).getPosition(getCurrentHandVisualCardPosition().x, getCurrentHandVisualCardPosition().y, cardSize.width, cardSize.height);
        animator.start(new Point(arrow.getX(), arrow.getY()), destinationPos, ((Arrow)arrow).getGraphicsIndex());

        gameState = GameState.PLAYER_CHOOSE;
    }

    private void checkForPlayerBust(int index)
    {
        player.checkForBustInHand(index);

        if (player.isBustedInHand(index))
        {
            if (player.isAnotherHand())
            {
                goToNextHand();
            }
            else
            {
                gameState = GameState.DEALER_DRAW;
            }
        }
        else
        {
            if (gameState == GameState.PLAYER_DOUBLE_DOWN)
            {
                if (player.isAnotherHand())
                {
                    goToNextHand();
                }
                else
                {
                    gameState = GameState.DEALER_DRAW;
                }
            }
            else
            {
                gameState = GameState.PLAYER_CHOOSE;
            }
        }
    }

    private void dealerDraw()
    {
        if (!animator.isPlaying())
        {
            revealFaceDownCard();
            
            if (dealerHand.getValueOfCards() < 17 && !player.isBlackjackOrBustedInAllHands())
            {
                drawCard(dealerHand, getNextDealerCardPosition(), false, false);
            }
            else
            {
                endRound();
            }
        }
    }

    private void revealFaceDownCard()
    {
        Graphic visualCardWithFaceDownImage = graphics.get(indexOfFirstVisualCardInGraphics + 1);
        visualCardWithFaceDownImage.setImage(dealerHand.getCard(0).getImage());
    }

    private void endRound()
    {
        determineWinner();
        //addToChipTray(player.getWinnings());
        
        gameState = GameState.CLEAR_BOARD;
    }

    private void determineWinner()
    {
        int winnings = 0;

        for (int i = 0; i < player.getMaxNumberOfHands(); i++)
        {
            if (player.getHand(i).isActive())
            {
                if (player.isBustedInHand(i))
                {
                    winnings += 0;
                }
                else if (dealerHand.getValueOfCards() > 21)
                {
                    if (player.isBlackjackInHand(i))
                    {
                        winnings += (int)(player.getInitialBet() * 2.5);
                    }
                    else if (player.isDoubledDownInHand(i))
                    {
                        winnings += (player.getInitialBet() * 2) * 2;
                    }
                    else
                    {
                        winnings += player.getInitialBet() * 2;
                    }
                }
                else if (dealerHand.getValueOfCards() > player.getValueOfCardsInHand(i))
                {
                    winnings += 0;
                }
                else if (player.getValueOfCardsInHand(i) == dealerHand.getValueOfCards())
                {
                    if (player.isDoubledDownInHand(i))
                    {
                        winnings += player.getInitialBet() * 2;
                    }
                    else
                    {
                        winnings += player.getInitialBet();
                    }
                }
                else if (player.getValueOfCardsInHand(i) > dealerHand.getValueOfCards())
                {
                    if (player.isBlackjackInHand(i))
                    {
                        winnings += (int)(player.getInitialBet() * 2.5);
                    }
                    else if (player.isDoubledDownInHand(i))
                    {
                        winnings += (player.getInitialBet() * 2) * 2;
                    }
                    else
                    {
                        winnings += player.getInitialBet() * 2;
                    }
                }
                else
                {
                    winnings += 0;
                }
            }
        }

        player.addChips(winnings);
        player.setWinnings(winnings);
    }

    private void clearBoard()
    {
        if (canClearBoard)
        {
            clearCards();
            reset();
            checkForShuffle();
        }
    }
    
    private void clearCards()
    {
        for (Hand hand : player.getHands())
        {
            hand.moveAllCardsToDeck(usedDeck);
        }

        dealerHand.moveAllCardsToDeck(usedDeck);

        graphics.subList(indexOfFirstVisualCardInGraphics, graphics.size()).clear();
    }

    private void checkForShuffle()
    {
        if (playingDeck.getNumberOfCards() < 52)
        {
            gameState = GameState.SHUFFLE_DECK;
        }
        else
        {
            gameState = GameState.PLAYER_BET;
        }
    }

    private void shuffleDeck()
    {
        System.out.println("\nShuffling!");
        
        usedDeck.moveAllCardsToDeck(playingDeck);
        playingDeck.shuffle();

        gameState = GameState.PLAYER_BET;
    }

    /* private void sortChipTray()
    {
        chipTray.sort(player.getChips());
        visualChips = chipTray.getVisualChips();
    }

    private void addToChipTray(int valueOfChipsToAdd)
    {
        chipTray.add(valueOfChipsToAdd);
        visualChips = chipTray.getVisualChips();
    }

    private void removeFromChipTray(int valueOfChipsToRemove)
    {
        chipTray.remove(valueOfChipsToRemove);
        visualChips = chipTray.getVisualChips();
    } */

    private void reset()
    {
        adjustPlayerBet();
        player.reset();

        dealerHand.reset();
        
        ((Arrow)arrow).reset();
        
        canClearBoard = false;
    }

    private void adjustPlayerBet()
    {
        if (player.getChips() >= minBet)
        {
            player.resetBet();
        }
        else
        {
            System.exit(1);
        }
    }

    // Called after the JPanel has been added to the JFrame component (initialize)
    @Override
    public void addNotify()
    {
        super.addNotify();

        thread = new Thread(this);
        thread.start();
    }

    private void gameLoop()
    {
        switch (gameState)
        {
            case PLAYER_BET:
                break;
            case PLACE_BET:
                placeBet();
                break;
            case RECEIVE_CARDS:
                receiveCards();
                break;
            case PLAYER_CHOOSE:
                checkForPlayerBlackjack(player.getCurrentHandIndex());
                break;
            case PLAYER_HIT:
                hit();
                break;
            case PLAYER_STAND:
                stand();
                break;
            case PLAYER_DOUBLE_DOWN:
                doubleDown();
                break;
            case PLAYER_SPLIT:
                split();
                break;
            case DEALER_DRAW:
                dealerDraw();
                break;
            case CLEAR_BOARD:
                clearBoard();
                break;
            case SHUFFLE_DECK:
                shuffleDeck();
                break;
        }
    }

    @Override
    public void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        draw(g);

        Toolkit.getDefaultToolkit().sync();
    }

    private void draw(Graphics g)
    {
        Graphics2D g2d = (Graphics2D)g;
        g2d.translate(0, cameraY);

        if (debugMode)
        {
            g2d.setFont(Screen.FONT);
            g2d.setColor(Color.WHITE);

            g2d.drawString("Pixel Blackjack v2.5.0", (20 * scale), (15 * scale));
            g2d.drawString("Player winnings: " + player.getWinnings(), (20 * scale), (30 * scale));
            g2d.drawString("Current hand: " + player.getCurrentHandIndex(), (20 * scale), (38 * scale));

            g2d.setFont(Screen.getScaledFont(3));
            g2d.setColor(new Color(0, 0, 0, 50));

            String text = "BlackJack pays 3 to 2";
            g2d.drawString(text, (screenSize.width / 2) - (int)(g2d.getFontMetrics().stringWidth(text) / 2), (screenSize.height / 2) + (int)(Screen.getScaledFont(3).getSize() / 2));

            for (int i = 0; i < player.getMaxNumberOfHands(); i++)
            {
                g2d.setStroke(new BasicStroke(scale));
                g2d.drawRect(splitMarginX + (i * splitOffsetX) - (int)Math.ceil(((double)scale / 2)), playerCardStartingPos.y - (int)Math.ceil(((double)scale / 2)), cardSize.width + scale, cardSize.height + scale);
            }
        }

        g2d.setFont(Screen.FONT);

        // Props
        g2d.drawImage(betSquare, betSquarePos.x, betSquarePos.y, betSquareSize.width, betSquareSize.height, null);
        g2d.drawImage(dealerTray, dealerTrayPos.x, dealerTrayPos.y, dealerTraySize.width, dealerTraySize.height, null);
        g2d.drawImage(infoTextArea, infoTextAreaPos.x, infoTextAreaPos.y, infoTextAreaSize.width, infoTextAreaSize.height, null);

        if (((Arrow)arrow).isActive())
        {
            g2d.drawImage(arrow.getImage(), arrow.getX(), arrow.getY(), arrow.getWidth(), arrow.getHeight(), null);
        }

        // Text
        g2d.setColor(Color.WHITE);
        g2d.drawString("Chips: ", infoTextAreaPos.x + (3 * scale), infoTextAreaPos.y + (8 * scale));
        g2d.drawString("Bet:    ", infoTextAreaPos.x + (3 * scale), infoTextAreaPos.y + (16 * scale));
        
        g2d.setColor(new Color(242, 204, 98));
        g2d.drawString(textManager.getFormattedNumber(player.getChips()), infoTextAreaPos.x + (3 * scale) + (22 * scale), infoTextAreaPos.y + (8 * scale));
        g2d.drawString(textManager.getFormattedNumber(player.getBet()), infoTextAreaPos.x + (3 * scale)  + (22 * scale), infoTextAreaPos.y + (16 * scale));

        // Bet area
        g2d.setColor(new Color(51, 51, 51));
        g2d.fillRect(0, screenSize.height, screenSize.width, (45 * scale));

        g2d.setColor(new Color(242, 204, 98));
        String betText = textManager.getFormattedNumber(player.getBet());
        g2d.drawString("Bet: " + betText,
                       textManager.getCenteredText("Bet: " + betText, g2d.getFontMetrics(), screenSize.width / 2),
                       screenSize.height + (25 * scale));

        g2d.setColor(new Color(68, 68, 68));
        g2d.fillRect(0, screenSize.height + (45 * scale), screenSize.width, (140 * scale));

        int i = 0;

        for (ChipValue chipValue : ChipValue.values())
        {
            g2d.setColor(new Color(51, 51, 51));
            g2d.fillRect(betRowMarginX + (i * betRowOffsetX), screenSize.height + (65 * scale), chipSize.width, (100 * scale));
            
            g2d.setColor(Color.WHITE);
            String chipText = textManager.getFormattedNumber(chipValue.getValue());
            g2d.drawString(chipText,
                           textManager.getCenteredText(chipText,
                                                       g2d.getFontMetrics(),
                                                       betRowMarginX + (i * betRowOffsetX) + ((int)((chipSize.width / scale) / 2) * scale) + (2 * scale)),
                           screenSize.height + (60 * scale));
        
            i++;
        }

        //g2d.drawImage(chipTrayShadowImg, leftChipTrayShadowPos.x, leftChipTrayShadowPos.y, chipTrayShadowSize.width, chipTrayShadowSize.height, null);
        //g2d.drawImage(chipTrayImg, leftChipTrayPos.x, leftChipTrayPos.y, chipTraySize.width, chipTraySize.height, null);

        //g2d.drawImage(chipTrayShadowImg, rightChipTrayShadowPos.x, rightChipTrayShadowPos.y, chipTrayShadowSize.width, chipTrayShadowSize.height, null);
        //g2d.drawImage(chipTrayImg, rightChipTrayPos.x, rightChipTrayPos.y, chipTraySize.width, chipTraySize.height, null);

        /* if (player.hasPlacedBet())
        {
            for (int i = 0; i < (player.getNumberOfHands()); i++) // CHANGE CHIP POSITIONS BELOW
            {
                if (player.getHand(i).isActive())
                {
                    if (player.isDoubledDownInHand(i))
                    {
                        g2d.drawImage(chipShadow, chipShadowStartingPos.x + chipDoubleDownOffset.x + (chipSplitOffset.x * i), chipShadowStartingPos.y, chipShadowSize.width, chipShadowSize.height, null);
                        g2d.drawImage(chipInBetSquare, chipStartingPos.x + chipDoubleDownOffset.x + (chipSplitOffset.x * i), chipStartingPos.y, chipSize.width, chipSize.height, null);
                    }
                    
                    g2d.drawImage(chipShadow, chipShadowStartingPos.x + (chipSplitOffset.x * i), chipShadowStartingPos.y, chipShadowSize.width, chipShadowSize.height, null);
                    g2d.drawImage(chipInBetSquare, chipStartingPos.x + (chipSplitOffset.x * i), chipStartingPos.y, chipSize.width, chipSize.height, null);
                }
            }
        } */

        for (Graphic visualCard : graphics.subList(indexOfFirstVisualCardInGraphics, graphics.size()))
        {   
            if (((VisualCard)visualCard).isRotated())
            {
                AffineTransform rotatedCard = AffineTransform.getTranslateInstance(visualCard.getX() + cardSize.height, visualCard.getY());
                AffineTransform rotatedShadow = AffineTransform.getTranslateInstance(visualCard.getX() + cardSize.height + scale, visualCard.getY() - scale);
                
                rotatedCard.rotate(Math.PI / 2);
                rotatedShadow.rotate(Math.PI / 2);
                rotatedCard.scale(scale, scale);
                rotatedShadow.scale(scale, scale);

                g2d.drawImage(cardShadow, rotatedShadow, null);
                g2d.drawImage(visualCard.getImage(), rotatedCard, null); 
            }
            else
            {
                g2d.drawImage(cardShadow, visualCard.getX() - scale, visualCard.getY() - scale, cardShadowSize.width, cardShadowSize.height, null);
                g2d.drawImage(visualCard.getImage(), visualCard.getX(), visualCard.getY(), cardSize.width, cardSize.height, null);
            }
        }

        /* for (VisualChip visualChip : visualChips)
        {
            g2d.drawImage(visualChip.getImage(), visualChip.getX(), visualChip.getY(), chipInChipTraySize.width, chipInChipTraySize.height, null);
        } */
    }

    @Override
    public void run()
    {
        long timeBefore, timeDifference, sleep, delay;

        timeBefore = System.currentTimeMillis();
        delay = 1000 / Screen.REFRESH_RATE;

        while (true)
        {
            gameLoop();
            animator.play();
            repaint();

            timeDifference = System.currentTimeMillis() - timeBefore;
            sleep = delay - timeDifference;

            if (sleep < 0)
            {
                sleep = 2;
            }

            try
            {
                Thread.sleep(sleep);
            }
            catch (InterruptedException e)
            {
                String msg = String.format("\nThread interrupted: %s", e.getMessage());
                System.out.println(msg);
            }

            timeBefore = System.currentTimeMillis();
        }
    }

    private class TAdapter extends KeyAdapter
    {
        @Override
        public void keyPressed(KeyEvent e)
        {
            int key = e.getKeyCode();

            if (!animator.isPlaying())
            {
                switch (key)
                {
                    case KeyEvent.VK_ENTER:
                        if (gameState == GameState.PLAYER_BET)
                        {
                            gameState = GameState.PLACE_BET;
                        }

                        if (gameState == GameState.PLAYER_CHOOSE)
                        {
                            gameState = GameState.PLAYER_HIT;
                        }

                        break;
                    case KeyEvent.VK_SPACE:
                        if (gameState == GameState.PLAYER_CHOOSE)
                        {
                            gameState = GameState.PLAYER_STAND;
                        }

                        if (gameState == GameState.CLEAR_BOARD)
                        {
                            canClearBoard = true;
                        }

                        break;
                    case KeyEvent.VK_D:
                        if (e.isControlDown() && e.isAltDown())
                        {
                            debugMode = !debugMode;
                        }
                        else
                        {
                            if (gameState == GameState.PLAYER_CHOOSE)
                            {
                                gameState = GameState.PLAYER_DOUBLE_DOWN;
                            }
                        }

                        break;
                    case KeyEvent.VK_TAB:
                        if (gameState == GameState.PLAYER_BET)
                        {
                            player.setBetToMax();

                            if (player.getBet() > maxBet)
                            {
                                player.setBet(maxBet);
                            }
                        }

                        break;
                    case KeyEvent.VK_SHIFT:
                        if (gameState == GameState.PLAYER_BET)
                        {
                            if (player.getChips() >= (minBet * 2))
                            {
                                player.setBetToHalf();
                            }
                        }

                        break;
                    case KeyEvent.VK_S:
                        if (gameState == GameState.PLAYER_CHOOSE && canSplit())
                        {
                            splitStage = SplitStage.INITIATE;
                            gameState = GameState.PLAYER_SPLIT;
                        }

                        break;
                    case KeyEvent.VK_P:
                        System.out.println("\nHands:");

                        for (Hand hand : player.getHands())
                        {
                            System.out.println(hand.isActive());
                        }

                        break;
                    case KeyEvent.VK_UP:
                        if (gameState == GameState.PLAYER_BET)
                        {
                            if (player.getBet() < maxBet && player.hasEnoughMoneyToIncreaseBet())
                            {
                                player.increaseBet(1);
                            }
                            else
                            {
                                if (!player.hasEnoughMoneyToIncreaseBet())
                                {
                                    player.setBet(player.getChips());
                                }
                                else
                                {
                                    player.setBet(maxBet);
                                }
                            }
                        }

                        break;
                    case KeyEvent.VK_DOWN:
                        if (gameState == GameState.PLAYER_BET)
                        {
                            if (player.getBet() > minBet)
                            {
                                player.decreaseBet(1);
                            }
                            else
                            {
                                player.setBet(minBet);
                            }
                        }

                        break;
                    case KeyEvent.VK_PAGE_UP:
                        cameraY += 5 * scale;

                        if (cameraY >= 0)
                        {
                            cameraY = 0;
                        }

                        break;
                    case KeyEvent.VK_PAGE_DOWN:
                        cameraY -= 5 * scale;

                        if (cameraY <= -(185 * scale))
                        {
                            cameraY = -(185 * scale);
                        }

                        break;
                    case KeyEvent.VK_ESCAPE:
                        if (gameState == GameState.PLAYER_BET)
                        {
                            System.exit(1);
                        }

                        break;
                }
            }
        }
    }

    private class Animator
    {
        private boolean isPlaying = false;

        private List<Point> startingPositions = new ArrayList<Point>();
        private List<Point> destinationPositions = new ArrayList<Point>();
        private List<Point> currentPositions = new ArrayList<Point>();
        
        private List<Integer> indexesInArrayList = new ArrayList<Integer>();

        private final double durationInSeconds = 0.435;

        private double newPosX;
        private double newPosY;

        private double progress;
        private int count;

        public boolean isPlaying()
        {
            return isPlaying;
        }

        public void play()
        {
            if (isPlaying)
            {
                if (progress < 1)
                {
                    count++;
                    progress = count / (durationInSeconds * Screen.REFRESH_RATE);
                }

                for (int i = 0; i < startingPositions.size(); i++)
                {
                    if (progress < 1)
                    {
                        newPosX = easeInOut((float)(progress * durationInSeconds), startingPositions.get(i).x, getDistance(i).x, (float)durationInSeconds);
                        newPosY = easeInOut((float)(progress * durationInSeconds), startingPositions.get(i).y, getDistance(i).y, (float)durationInSeconds);
                    
                        currentPositions.get(i).setLocation((int)Math.round(newPosX), (int)Math.round(newPosY));

                        graphics.get(indexesInArrayList.get(i)).setX(currentPositions.get(i).x); // function returning: visualCards.get(indexesInArrayList.get(i)) ?
                        graphics.get(indexesInArrayList.get(i)).setY(currentPositions.get(i).y);
                    }
                    else
                    {
                        graphics.get(indexesInArrayList.get(i)).setX(destinationPositions.get(i).x);
                        graphics.get(indexesInArrayList.get(i)).setY(destinationPositions.get(i).y);

                        if (i == startingPositions.size() - 1)
                        {
                            isPlaying = false;
                            
                            clearArrayLists();
                        }
                    }
                }
            }
        }

        private void clearArrayLists()
        {
            startingPositions.clear();
            destinationPositions.clear();
            currentPositions.clear();
            indexesInArrayList.clear();
        }

        public void start(Point startingPos, Point destinationPos, int indexInArrayList)
        {
            count = 0;
            progress = 0;

            startingPositions.add(new Point(startingPos.x, startingPos.y));
            destinationPositions.add(new Point(destinationPos.x, destinationPos.y));
            currentPositions.add(new Point(startingPos.x, startingPos.y));

            indexesInArrayList.add(indexInArrayList);

            isPlaying = true;
        }

        private float easeInOut(float timeElapsedInSeconds, float startingPos, float distance, float durationInSeconds)
        {
            if ((timeElapsedInSeconds /= (durationInSeconds / 2)) < 1)
            {
                return (distance / 2) * timeElapsedInSeconds * timeElapsedInSeconds * timeElapsedInSeconds + startingPos;
            }
            
            return (distance / 2) * ((timeElapsedInSeconds -= 2) * timeElapsedInSeconds * timeElapsedInSeconds + 2) + startingPos;
        }

        private Point getDistance(int index)
        {
            return new Point(destinationPositions.get(index).x - startingPositions.get(index).x, destinationPositions.get(index).y - startingPositions.get(index).y);
        }
    }
}