package com.github.mrchris2000.discordLadder;

import com.github.mrchris2000.discordLadder.infra.AutoCompletes;
import com.github.mrchris2000.discordLadder.listeners.SlashCommandListener;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.*;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.*;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.blockhound.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

public class LadderBot {
    private static final Logger LOGGER = LoggerFactory.getLogger(LadderBot.class);

    public static Map<String, String> envs = System.getenv();

    public static String tournament_role = envs.get("ROLE");

    public static String discord_token = envs.get("DISCORD_TOKEN");

    public static String GUILD_NAME = envs.get("GUILD_NAME");

    public static Snowflake role_id;

    public static void main(String[] args) throws Exception {

        BlockHound.install();

        //Creates the database client and connects to the database
        LOGGER.debug("Role: " + LadderBot.tournament_role);
        LOGGER.debug("Token: " + LadderBot.discord_token);
        LOGGER.debug("Guild: " + LadderBot.GUILD_NAME);
        Guild guild = null;

        //Creates the gateway client and connects to the gateway
        final GatewayDiscordClient client = DiscordClientBuilder.create(LadderBot.discord_token).build()
                .gateway()
                .setEnabledIntents(IntentSet.of(Intent.GUILD_MEMBERS))
                .login()
                .block();

        //Set up user completes..?
        Iterator<Guild> guilds = client.getGuilds().toIterable().iterator();
        while (guilds.hasNext())
        {
            guild = guilds.next();
            if (guild.getName().contains(LadderBot.GUILD_NAME)) {
                break;
            }
        }

        Iterator<Role> serverRoles = guild.getRoles().toIterable().iterator();
        while (serverRoles.hasNext()) {
            Role serverRole = serverRoles.next();
            if (serverRole.getName().contains(LadderBot.tournament_role)) {
                role_id = serverRole.getId();
            }
        }
        AutoCompletes completions = new AutoCompletes(client);
        //completions.addPlayers();

        /* Call our code to handle creating/deleting/editing our global slash commands.

        We have to hard code our list of command files since iterating over a list of files in a resource directory
         is overly complicated for such a simple demo and requires handling for both IDE and .jar packaging.

         Using SpringBoot we can avoid all of this and use their resource pattern matcher to do this for us.
        */
        List<String> commands = List.of("team.json", "player.json", "challenge.json", "ladder.json");
        try {
            new GlobalCommandRegistrar(client.getRestClient(), guild).registerCommands(commands);
        } catch (Exception e) {
            LOGGER.error("Error trying to register global slash commands", e);
        }

        //Register our slash command listener
        SlashCommandListener listener = new SlashCommandListener( completions, guild);
        client.on(ChatInputAutoCompleteEvent.class, listener::complete).subscribe();
        client.on(ButtonInteractionEvent.class, listener::buttons).subscribe();
        client.on(ChatInputInteractionEvent.class, listener::handle)
                .then(client.onDisconnect())
                .block(); // We use .block() as there is not another non-daemon thread and the jvm would close otherwise.

    }
}
