package com.github.mrchris2000.discordLadder.commands;

import com.github.mrchris2000.discordLadder.LadderBot;
import com.github.mrchris2000.discordLadder.infra.AutoCompletes;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class LadderCommand implements SlashCommand {
    public LadderCommand(AutoCompletes completes, Guild guild) {
        this.completes = completes;
        this.guild = guild;

        role_id = LadderBot.role_id;
    }

    @Override
    public String getName() {
        return "ladder";
    }

    private Connection connection;

    private final AutoCompletes completes;

    private final Guild guild;

    private static final Logger LOGGER = LoggerFactory.getLogger(LadderBot.class);

    private final Snowflake role_id;


    public Mono<Void> complete(ChatInputAutoCompleteEvent event) {
        //There are no completes needed for this command.
        return null;
    }

    public Mono<Message> buttons(ButtonInteractionEvent event) {
        //There are no buttons to be used for this command.
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

        Statement st = null;
        ResultSet rs = null;
        try {
            final Properties props = new Properties();
            props.setProperty("user", "ladder");
            props.setProperty("password", "discPwd#!");
            connection = DriverManager.getConnection("jdbc:postgresql://192.168.0.20:5432/discladder", props);
            st = connection.createStatement();

            //Replace dummy team data with info from the backend.
            EmbedCreateSpec embed = EmbedCreateSpec.builder()
                    .color(Color.of(255, 153, 0))
                    .title("Our teams are currently:")
                    .author("Current state of the leaderboard", "", "https://cdn.discordapp.com/avatars/1198703676987023450/8d7ab02c29bf51ac5f7c70615a2c3afb.png")

                    .image("https://images-wixmp-ed30a86b8c4ca887773594c2.wixmp.com/f/3db49b4c-f1c1-4bb6-85db-28aef1446bfb/d7xv7ks-55352abf-cd58-487f-ba38-7310f84bdf01.jpg?token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1cm46YXBwOjdlMGQxODg5ODIyNjQzNzNhNWYwZDQxNWVhMGQyNmUwIiwiaXNzIjoidXJuOmFwcDo3ZTBkMTg4OTgyMjY0MzczYTVmMGQ0MTVlYTBkMjZlMCIsIm9iaiI6W1t7InBhdGgiOiJcL2ZcLzNkYjQ5YjRjLWYxYzEtNGJiNi04NWRiLTI4YWVmMTQ0NmJmYlwvZDd4djdrcy01NTM1MmFiZi1jZDU4LTQ4N2YtYmEzOC03MzEwZjg0YmRmMDEuanBnIn1dXSwiYXVkIjpbInVybjpzZXJ2aWNlOmZpbGUuZG93bmxvYWQiXX0.ojmjc2svRjM8ia-m5lZA7CU55VrLp-KMrRlcilW247I")
                    .build();

            st.executeQuery("SELECT\n"
                    + "    t.team_name,\n"
                    + "    l.rank AS ladder_position,\n"
                    + "    COALESCE(m.total_matches, 0) AS matches_played\n"
                    + "FROM\n"
                    + "    teams t\n"
                    + "JOIN\n"
                    + "    ladder l ON t.team_id = l.team_id\n"
                    + "LEFT JOIN\n"
                    + "    (\n"
                    + "        SELECT\n"
                    + "            team_one_id AS team_id, COUNT(*) AS total_matches\n"
                    + "        FROM\n"
                    + "            matches\n"
                    + "        WHERE\n"
                    + "            replay_id != 0\n"
                    + "        GROUP BY\n"
                    + "            team_one_id\n"
                    + "\n"
                    + "        UNION ALL\n"
                    + "\n"
                    + "        SELECT\n"
                    + "            team_two_id AS team_id, COUNT(*) AS total_matches\n"
                    + "        FROM\n"
                    + "            matches\n"
                    + "        WHERE\n"
                    + "            replay_id != 0\n"
                    + "        GROUP BY\n"
                    + "            team_two_id\n"
                    + "    ) m ON t.team_id = m.team_id\n"
                    + "GROUP BY\n"
                    + "    t.team_name, l.rank, m.total_matches\n"
                    + "ORDER BY\n"
                    + "    l.rank ASC;");
            rs = st.getResultSet();
            EmbedCreateSpec.Builder dynamic = EmbedCreateSpec.builder().from(embed);
            dynamic.addField("Team", "", true);
            dynamic.addField("Total games played", "", true);
            dynamic.addField("Ladder position", "", true);
            while (rs.next()) {
                dynamic.addField("", rs.getString("team_name"), true);
                dynamic.addField("", rs.getString("matches_played"), true);
                dynamic.addField("", rs.getString("ladder_position"), true);
            }
            embed = dynamic.build();

            return event.createFollowup().withEmbeds(embed);
        } catch (Exception e) {
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
                .withEphemeral(true).withContent("Challenge command failed, no valid option found");
    }


}
