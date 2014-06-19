package deathcap.wsmc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Links a random "key" to the player's identity for websocket authentication
public class UserIdentityLinker implements Listener, CommandExecutor, UserAuthenticator {

    private Map<String, String> keys = new HashMap<String, String>(); // TODO: persist TODO: UUID? but need player name anyway
    private SecureRandom random = new SecureRandom();
    private final String webURL;
    private final boolean announceOnJoin;
    private final boolean allowAnonymous;
    private final WsmcPlugin plugin;

    public UserIdentityLinker(String webURL, boolean announceOnJoin, boolean allowAnonymous, WsmcPlugin plugin) {
        this.webURL = webURL;
        this.announceOnJoin = announceOnJoin;
        this.allowAnonymous = allowAnonymous;
        this.plugin = plugin;
    }

    // Try to login, returning username if successful, null otherwise
    public String verifyLogin(String clientCredential) {
        String[] a = clientCredential.split(":");
        if (a.length == 0 || a.length > 2) {
            System.out.println("invalid credential format received: "+clientCredential);
            return null;
        }

        if (a.length == 1) {
            if (!this.allowAnonymous) {
                System.out.println("user attempted to login anonymously (but denied by minecraft.allow-anonymous false): "+clientCredential);
                return null;
            }
            return clientCredential; // WARNING: anyone can login as anyone if this is enabled (off by default)
        }

        String username = a[0];
        String actualKey = a[1];
        String expectedKey = this.keys.get(username);
        if (expectedKey == null) {
            System.out.println("no such user recognized for "+clientCredential+", need to login first, run /web, or enable minecraft.allow-anonymous");
            return null;
        }
        if (!expectedKey.equals(actualKey)) {
            System.out.println("login failure for "+clientCredential+", expected "+expectedKey);
            return null;
        }
        // TODO: return failures to ws

        System.out.println("successfully verified websocket connection for "+username);
        return username;
    }


    // Generate a random secret key, suitable for embedding in a URL
    private String newRandomKey() {
        byte[] bytes = new byte[4]; // TODO: more bytes?
        random.nextBytes(bytes);
        int n = bytes[0] | (bytes[1] << 8) | (bytes[2] << 16) | (bytes[3] << 24);
        String s = ""+n; // TODO: base36(?) encoding
        return s;
    }

    public String getOrGenerateUserKey(String name) {
        //UUID uuid = player.getUniqueId(); // TODO?
        //String name = player.getName();

        String key = keys.get(name);
        if (key == null) {
            key = newRandomKey();
            keys.put(name, key);
            System.out.println("new key generated for "+name+": "+key);
        }

        return key;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!this.announceOnJoin) return;

        Player player = event.getPlayer();
        /* TODO: how do we send links? can generate this JSON
        with http://deathcap.github.io/tellraw2dom/ but need to find proper API,
        sendRawMessage() seems to just send {"text":...}
        String raw =
        "{" +
            "\"extra\": [" +
            "{" +
                "\"text\": \"Web client enabled (click to view)\"," +
                    "\"bold\": \"true\"," +
                    "\"clickEvent\": {" +
                        "\"action\": \"open_url\"," +
                        "\"value\": \"https://github.com\"" +
                    "}" +
                "}" +
            "]" +
        "}";
         */

        // TODO: don't show if client brand is our own
        // TODO: option to only show on first connect

        this.tellPlayer(player, player);
    }

    // Give a player a URL to authenticate and join over the websocket
    private void tellPlayer(Player whom, CommandSender destination) {
        String username = whom.getName();
        this.tellPlayer(username, destination);
    }

    private void tellPlayer(String username, CommandSender destination) {
        String key = this.getOrGenerateUserKey(username);
        String msg = "Web client enabled: "+webURL+"#"+username+":"+key; // TODO: urlencode

        destination.sendMessage(msg);
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (commandSender instanceof Player) {
            Player player = (Player)commandSender;
            this.tellPlayer(player, player);

            return true;
        } else {
            if (args.length < 1) {
                commandSender.sendMessage("player name required for /web");
                return false;
            }

            String playerName = args[0];
            /*
            Player player = this.plugin.getServer().getPlayer(playerName);
            if (player == null) {
                commandSender.sendMessage("no such player "+playerName);
                return false;
            }
            */

            this.tellPlayer(playerName, commandSender);

            return false;
        }
    }
}