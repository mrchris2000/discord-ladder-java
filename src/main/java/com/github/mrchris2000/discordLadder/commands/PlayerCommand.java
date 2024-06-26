package com.github.mrchris2000.discordLadder.commands;

import com.github.mrchris2000.discordLadder.LadderBot;
import com.github.mrchris2000.discordLadder.infra.AutoCompletes;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.*;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class PlayerCommand implements SlashCommand {
    public PlayerCommand(AutoCompletes completes, Guild guild) {
        this.completes = completes;
        this.guild = guild;

        //Determine role
        role_id = LadderBot.role_id;
    }

    @Override
    public String getName() {
        return "player";
    }

    private Connection connection;

    private final Guild guild;

    private final AutoCompletes completes;

    private static final Logger LOGGER = LoggerFactory.getLogger(LadderBot.class);

    private final Snowflake role_id;

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
                    //return event.respondWithSuggestions(completes.getPlayerNames());
                }
            }
        }
        return event.respondWithSuggestions(suggestions);
    }

    public Mono<Message> buttons(ButtonInteractionEvent event) {
        return null;
    }

    @Override
    public Mono<Message> handle(ChatInputInteractionEvent event) {
        return event.deferReply().then(processEvent(event));
    }

    public Mono<Message> processEvent(ChatInputInteractionEvent event) {
        /*
        Since slash command options are optional according to discord, we will wrap it into the following function
        that gets the value of our option as a String without chaining several .get() on all the optional values

        In this case, there is no fear it will return empty/null as this is marked "required: true" in our json.
         */
        //System.out.println(event.toString());
        //ApplicationCommandInteractionOption root = event.getOption("team").get();
        Statement st = null;
        try {
            final Properties props = new Properties();
            props.setProperty("user", "ladder");
            props.setProperty("password", "discPwd#!");
            connection = DriverManager.getConnection("jdbc:postgresql://192.168.0.20:5432/discladder", props);
            Member user = event.getInteraction().getMember().get();
            st = connection.createStatement();
            if (event.getOption("join").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("join").get();
                String faf_name = type.getOption("fafname").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                LOGGER.debug("Submitting user? : " + user.getUsername() + " :: " + user.getDisplayName());

                //Assign role
                user.addRole(role_id);

                //Add user to the database
                st = connection.prepareStatement("INSERT INTO players (player_name, discord_id, fafName, active) VALUES (?, ?, ?, ?) ON CONFLICT (player_name) DO UPDATE SET active=true;");
                ((PreparedStatement) st).setString(1, user.getUsername());
                ((PreparedStatement) st).setString(2, user.getId().asString());
                ((PreparedStatement) st).setString(3, faf_name);
                ((PreparedStatement) st).setBoolean(4, true);
                int row = ((PreparedStatement) st).executeUpdate();


                return event.createFollowup()
                        .withEphemeral(false).withContent("Hey, <@" + user.getId().asString() + "> you've joined the tournament");
            } else if (event.getOption("update").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("update").get();
                String faf_name = type.getOption("fafname").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                LOGGER.debug("Submitting user? : " + user.getUsername() + " :: " + user.getDisplayName() + " :: discord_id : " + user.getId().asString());


                //Update the users faf name in the database
                st = connection.prepareStatement("UPDATE players SET fafName=? WHERE player_name=?");
                ((PreparedStatement) st).setString(1, faf_name);
                ((PreparedStatement) st).setString(2, user.getUsername());
                int row = ((PreparedStatement) st).executeUpdate();

                return event.createFollowup()
                        .withEphemeral(false).withContent("Thanks, <@" + user.getId().asString() + ">, your FAF name is now set to " + faf_name);
            } else if (event.getOption("leave").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("leave").get();

                LOGGER.debug("Submitting user? : " + user.getUsername() + " :: " + user.getDisplayName() + " :: discord_id : " + user.getId().asString());

                //Remove role
                user.removeRole(role_id);

                //Update user to the database
                st = connection.prepareStatement("UPDATE players SET active=FALSE WHERE player_name=?");
                ((PreparedStatement) st).setString(1, user.getUsername());
                int row = ((PreparedStatement) st).executeUpdate();

                return event.createFollowup()
                        .withEphemeral(false).withContent("Sad to see you go, <@" + user.getId().asString() + ">");
            }
        } catch (Exception e) {
            if (e.getMessage().contains("duplicate")) {
                return event.createFollowup()
                        .withEphemeral(false).withContent("Player is already a tournament member");
            }
            e.printStackTrace();
        } finally {
            try {
                if (st != null) {
                    st.close();
                }
                connection.close();
            } catch (Exception e) {
                //Well this is fucked then...
                e.printStackTrace();
            }
        }
        return  event.createFollowup()
                .withEphemeral(true).withContent("Player command failed, no valid option found");
    }


}
