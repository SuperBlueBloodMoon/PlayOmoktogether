package omok_server;

import omok_server.data.Role;
import omok_shared.OmokMsg;

import java.util.UUID;
import java.util.Vector;

public class GameRoom {
    private final String title;
    private final Vector<Player> playerList = new Vector<Player>();
    private Player owner;
    private final OmokServer server;
    private final String roomId;

    private omokMethod logic = new omokMethod();
    private int currentTurnColor = 1;
    private boolean isPlaying = false;
    private int index = 0;

    private int MAX_PLAYERS = 2;

    public GameRoom(String title, Player owner, OmokServer server) {
        this.server = server;
        this.title = title;
        this.owner = owner;
        this.playerList.add(owner);
        this.roomId = UUID.randomUUID().toString();
        owner.setIndex(index);
        index++;
        setColor(owner);
    }

    public void broadcastGameRoom(OmokMsg msg) {
        for (Player player : playerList) {
            try {
                player.getClientHandler().send(msg);
            } catch (Exception e) {
                System.err.println("메시지 전송 실패: " + player.getClientHandler().getUid());
            }
        }
    }

    public int getPlayerCount() {
        return playerList.size();
    }

    public void enterPlayer(Player player) {
        if (playerList.size() >= MAX_PLAYERS) {
            player.setRole(Role.SPECTATOR);
        } else {
            setColor(player);
        }
        player.setRole(Role.PLAYER);
        player.setIndex(index);
        index++;

        playerList.add(player);
    }

    public void setColor(Player player) {
        if (player.getIndex() % 2 == 0) {
            player.setColor(1);
        } else {
            player.setColor(2);
        }
    }

    public synchronized void exitPlayer(Player player) {
        playerList.remove(player);

        if (player == this.owner) {
            index = 0;
            for (Player gPlayer : playerList) {
                if (gPlayer.getRole() == Role.SPECTATOR ) {
                    break;
                }
                gPlayer.setIndex(index);
                if (gPlayer.getIndex() == 0) {
                    owner = gPlayer;
                }
                setColor(gPlayer);
                index++;
            }
        }

        if (playerList.isEmpty()) {
            server.removeRoom(this);
        }
    }

    public String getTitle() {
        return title;
    }

    public void startGame() {
        if (isPlaying) return;

        logic.init();
        currentTurnColor = 1;
        isPlaying = true;

        broadcastGameRoom(new OmokMsg(String.valueOf(owner.getClientHandler().getUid()), OmokMsg.MODE_GAME_START, "Start"));
    }

    public boolean isOwner(Player user) {
        return user == owner;
    }
    public Player getOwner() {
        return owner;
    }

    public String getPlayersForClient() {
        String players = "";
        for (Player player : playerList) {
            if (player == this.owner) {
                players += player.getClientHandler().getUid() + " (방장/흑),";
            } else if (player.getRole() == Role.PLAYER) {
                if (player.getIndex() % 2 == 0) {
                    players += player.getClientHandler().getUid() + " (흑),";
                } else {
                    players += player.getClientHandler().getUid() + " (백),";
                }
            } else {
                players += player.getClientHandler().getUid() + " (관전자),";
            }
        }
        if (!players.isEmpty()) {
            players = players.substring(0, players.length() - 1);
        }
        return players;
    }
    public String getRoomId() {
        return roomId;
    }

}
