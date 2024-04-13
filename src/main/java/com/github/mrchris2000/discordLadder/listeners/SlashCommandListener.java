package com.github.mrchris2000.discordLadder.listeners;

import com.github.mrchris2000.discordLadder.commands.*;
import com.github.mrchris2000.discordLadder.infra.AutoCompletes;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public class SlashCommandListener {
    //An array list of classes that implement the SlashCommand interface
    private final static List<SlashCommand> commands = new ArrayList<>();

    Connection connection;

    public SlashCommandListener(Connection connection, AutoCompletes completes, Guild guild, Logger LOGGER) {
        //We register our commands here when the class is initialized
        commands.add(new TeamCommand(connection, completes, guild, LOGGER));
        commands.add(new PlayerCommand(connection, completes, guild, LOGGER));
        commands.add(new ChallengeCommand(connection, completes, guild, LOGGER));
        commands.add(new LadderCommand(connection, completes, guild, LOGGER));
    }

    public Mono<Void> handle(ChatInputInteractionEvent event) {
        // Convert our array list to a flux that we can iterate through
        return Flux.fromIterable(commands)
                //Filter out all commands that don't match the name of the command this event is for
                .filter(command -> command.getName().equals(event.getCommandName()))
                // Get the first (and only) item in the flux that matches our filter
                .next()
                //have our command class handle all the logic related to its specific command.
                .flatMap(command -> command.handle(event));
    }

    public Mono<Void> complete(ChatInputAutoCompleteEvent event) {
        // Convert our array list to a flux that we can iterate through
        return Flux.fromIterable(commands)
                //Filter out all commands that don't match the name of the command this event is for
                .filter(command -> command.getName().equals(event.getCommandName()))
                // Get the first (and only) item in the flux that matches our filter
                .next()
                //have our command class handle all the logic related to its specific command.
                .flatMap(command -> command.complete(event));
    }

    public Mono<Message> buttons(ButtonInteractionEvent event) {
        // Convert our array list to a flux that we can iterate through
        String origin = "";
        if (event.getCustomId().contains("challenge")) {
            origin = "challenge";
        } else if (event.getCustomId().contains("ladder")) {
            origin = "ladder";
        }
        final String cmd = origin;

        return Flux.fromIterable(commands)

                //Filter out all commands that don't match the name of the command this event is for
                .filter(command -> command.getName().equals(cmd))
                // Get the first (and only) item in the flux that matches our filter
                .next()
                //have our command class handle all the logic related to its specific command.
                .flatMap(command -> command.buttons(event));
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }
}
