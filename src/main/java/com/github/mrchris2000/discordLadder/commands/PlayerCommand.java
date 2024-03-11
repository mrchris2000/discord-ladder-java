package com.github.mrchris2000.discordLadder.commands;

import com.github.mrchris2000.discordLadder.infra.AutoCompletes;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PlayerCommand implements SlashCommand {
    public PlayerCommand(Connection connection, AutoCompletes completes) {
        this.connection = connection;
        this.completes = completes;
    }

    @Override
    public String getName() {
        return "team";
    }

    private final Connection connection;

    private final AutoCompletes completes;

    public Mono<Void> complete(ChatInputAutoCompleteEvent event) {
        Statement st = null;
        ResultSet rs = null;
        List<ApplicationCommandOptionChoiceData> suggestions = new ArrayList<>();
        if ("team".equals(event.getCommandName())) {
            if (event.getOption("create").isPresent()) {
                return event.respondWithSuggestions(completes.getTeamNames());
            } else if (event.getOption("stats").isPresent()) {
                return event.respondWithSuggestions(completes.getTeamNames());
            } else if (event.getOption("add").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("add").get();
                /*
                    We need the subtype of the option.
                    Options are collective, so the current option is the last in the list.
                 */
                String option = type.getOptions().get(type.getOptions().size() - 1).getName();
                if ("team_name".equals(option)) {
                    return event.respondWithSuggestions(completes.getTeamNames());
                } else {
                    return event.respondWithSuggestions(completes.getPlayerNames());
                }
            }
        }
        return event.respondWithSuggestions(suggestions);

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
        Statement st = null;
        try {
            st = connection.createStatement();
            if (event.getOption("join").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("join").get();

                String name = type.getOption("team_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                st = connection.prepareStatement("INSERT INTO player (player_name) VALUES (?)");
                ((PreparedStatement) st).setString(1, name);
                int row = ((PreparedStatement) st).executeUpdate();

                return event.reply()
                        .withEphemeral(false).withContent("<@508675578229162004> Added team: " + name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (st != null) {
                    st.close();
                }
            } catch (Exception e) {
                //Well this is fucked then...
                e.printStackTrace();
            }
        }
        return  event.reply()
                .withEphemeral(true).withContent("Team command failed, no valid option found");
    }


}
