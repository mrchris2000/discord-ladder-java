package com.github.mrchris2000.discordLadder;

import com.github.mrchris2000.discordLadder.infra.AutoCompletes;
import com.github.mrchris2000.discordLadder.listeners.SlashCommandListener;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.command.*;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.discordjson.json.*;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class LadderBot {
    private static final Logger LOGGER = LoggerFactory.getLogger(LadderBot.class);

    public static void main(String[] args) throws Exception {
        //Creates the database client and connects to the database
        final Properties props = new Properties();
        props.setProperty("user", "ladder");
        props.setProperty("password", "discPwd#!");
        final Connection connection = DriverManager.getConnection("jdbc:postgresql://192.168.0.20:5432/discladder", props);


        //Creates the gateway client and connects to the gateway
        final GatewayDiscordClient client = DiscordClientBuilder.create("MTE5ODcwMzY3Njk4NzAyMzQ1MA.G4Qh7M.sLXpAdfAMHmLggihWwXcsPplOfvn5W35F-aTkE").build()
                .gateway()
                .setEnabledIntents(IntentSet.of(Intent.GUILD_MEMBERS))
                .login()
                .block();

        //Set up user completes..?
        AutoCompletes completions = new AutoCompletes(client, connection);
        completions.addPlayers();

        /* Call our code to handle creating/deleting/editing our global slash commands.

        We have to hard code our list of command files since iterating over a list of files in a resource directory
         is overly complicated for such a simple demo and requires handling for both IDE and .jar packaging.

         Using SpringBoot we can avoid all of this and use their resource pattern matcher to do this for us.
        */
        List<String> commands = List.of( "team.json", "player.json", "challenge.json");
        try {
            new GlobalCommandRegistrar(client.getRestClient(), connection).registerCommands(commands);
        } catch (Exception e) {
            LOGGER.error("Error trying to register global slash commands", e);
        }

        //Register our slash command listener
        SlashCommandListener listener = new SlashCommandListener(connection, completions);
        client.on(ChatInputAutoCompleteEvent.class, listener::complete).subscribe();

        client.on(ChatInputInteractionEvent.class, listener::handle)
                .then(client.onDisconnect())
                .block(); // We use .block() as there is not another non-daemon thread and the jvm would close otherwise.

    }
}