package com.github.mrchris2000.discordLadder.commands;

import com.github.mrchris2000.discordLadder.infra.AutoCompletes;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.*;
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
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PlayerCommand implements SlashCommand {
    public PlayerCommand(Connection connection, AutoCompletes completes, Guild guild, Logger LOGGER) {
        this.connection = connection;
        this.completes = completes;
        this.guild = guild;
        this.LOGGER = LOGGER;
    }

    @Override
    public String getName() {
        return "player";
    }

    private final Connection connection;

    private final Guild guild;

    private final AutoCompletes completes;

    private final Logger LOGGER;

    public Mono<Void> complete(ChatInputAutoCompleteEvent event) {
        Statement st = null;
        ResultSet rs = null;
        List<ApplicationCommandOptionChoiceData> suggestions = new ArrayList<>();
        if (getName().equals(event.getCommandName())) {
            if (event.getOption("create").isPresent()) {
                return event.respondWithSuggestions(completes.getTeamNames());
            } else if (event.getOption("stats").isPresent()) {
                return event.respondWithSuggestions(completes.getTeamNames());
            } else if (event.getOption("join").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("join").get();
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

    public Mono<Message> buttons(ButtonInteractionEvent event) {
        return null;
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
            Member user = event.getInteraction().getMember().get();
            st = connection.createStatement();
            if (event.getOption("join").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("join").get();

                LOGGER.debug("Submitting user? : " + user.getUsername() + " :: " + user.getDisplayName());

                //Assign role
                Iterator<Role> serverRoles = guild.getRoles().toIterable().iterator();
                while (serverRoles.hasNext()) {
                    Role serverRole = serverRoles.next();
                    LOGGER.debug("Looping roles");
                    if(serverRole.getName().contains("2v2 Participant")){
                        LOGGER.debug("Found role");
                        user.addRole(serverRole.getId()).block();
                        LOGGER.debug("Added role");
                    }
                }
                //Add user to the database
                st = connection.prepareStatement("INSERT INTO players (player_name, discord_id, fafName, active) VALUES (?, ?, ?, ?) ON CONFLICT (player_name) DO UPDATE SET active=true;");
                ((PreparedStatement) st).setString(1, user.getUsername());
                ((PreparedStatement) st).setString(2, user.getId().asString());
                ((PreparedStatement) st).setString(3, user.getDisplayName());
                ((PreparedStatement) st).setBoolean(4, true);
                int row = ((PreparedStatement) st).executeUpdate();


                return event.reply()
                        .withEphemeral(false).withContent("Hey, <@" + user.getId().asString() + "> you've joined the tournament");
            } else if (event.getOption("leave").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("leave").get();

                LOGGER.debug("Submitting user? : " + user.getUsername() + " :: " + user.getDisplayName() + " :: discord_id : "+ user.getId().asString());

                //Remove role
                Iterator<Role> serverRoles = guild.getRoles().toIterable().iterator();
                while (serverRoles.hasNext()) {
                    Role serverRole = serverRoles.next();
                    LOGGER.debug("Looping roles");
                    if (serverRole.getName().contains("2v2 Participant")) {
                        LOGGER.debug("Found role");
                        user.removeRole(serverRole.getId()).block();
                        LOGGER.debug("Added role");
                    }
                }
                //Add user to the database
                st = connection.prepareStatement("UPDATE players SET active=FALSE WHERE player_name=?");
                ((PreparedStatement) st).setString(1, user.getUsername());
                int row = ((PreparedStatement) st).executeUpdate();

                return event.reply()
                        .withEphemeral(false).withContent("Sad to see you go, <@" + user.getId().asString() + ">");
            }
        } catch (Exception e) {
            if(e.getMessage().contains("duplicate")){
                return event.reply()
                        .withEphemeral(false).withContent("Player is already a tournament member");
            }
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
                .withEphemeral(true).withContent("Player command failed, no valid option found");
    }


}
