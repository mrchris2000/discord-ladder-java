package com.github.mrchris2000.discordLadder.commands;

import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import reactor.core.publisher.Mono;

import java.sql.Connection;

/**
 * A simple interface defining our slash command class contract.
 *  a getName() method to provide the case-sensitive name of the command.
 *  and a handle() method which will house all the logic for processing each command.
 */
public interface SlashCommand {

    String getName();

    Mono<Message> handle(ChatInputInteractionEvent event);

    Mono<Void> complete(ChatInputAutoCompleteEvent event);

    Mono<Message> buttons(ButtonInteractionEvent event);
}
