package com.github.mrchris2000.discordLadder.commands;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

public class TeamCommand implements SlashCommand {
    @Override
    public String getName() {
        return "team";
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        /*
        Since slash command options are optional according to discord, we will wrap it into the following function
        that gets the value of our option as a String without chaining several .get() on all the optional values

        In this case, there is no fear it will return empty/null as this is marked "required: true" in our json.
         */
        //System.out.println(event.toString());
        //ApplicationCommandInteractionOption root = event.getOption("team").get();

        try {
            if (event.getOption("add").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("add").get();

                String name = type.getOption("name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                return event.reply()
                        .withEphemeral(false).withContent("Adding team: " + name);
            } else if (event.getOption("list").isPresent()) {

                return event.reply()
                        .withEphemeral(false).withContent("Current teams: ---");
            } else if (event.getOption("remove").isPresent()) {

                return event.reply()
                        .withEphemeral(false).withContent("Removing xxx from Team yyy");
            } else if (event.getOption("stats").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("stats").get();

                String team = type.getOption("team").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                return event.reply()
                        .withEphemeral(false).withContent("Team stats for " + team + " : --");
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return  event.reply()
                .withEphemeral(true).withContent("Team command failed, no valid option found");
    }
}