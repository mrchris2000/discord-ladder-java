package com.github.mrchris2000.discordLadder.commands;

import com.github.mrchris2000.discordLadder.LadderBot;
import com.github.mrchris2000.discordLadder.infra.AutoCompletes;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.Role;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

public class ChallengeCommand implements SlashCommand {
    public ChallengeCommand(Connection connection, AutoCompletes completes, Guild guild, Logger LOGGER) {
        this.connection = connection;
        this.completes = completes;
        this.guild = guild;
        this.LOGGER = LOGGER;
    }

    //Challenge 2 up at most
    //No response after 5 days auto default
    //default means challenger goes above challengee



    @Override
    public String getName() {
        return "challenge";
    }

    private final Connection connection;

    private final AutoCompletes completes;

    private final Guild guild;

    private final Logger LOGGER;

    public Mono<Void> complete(ChatInputAutoCompleteEvent event) {
        List<ApplicationCommandOptionChoiceData> suggestions = new ArrayList<>();
        if (getName().equals(event.getCommandName())) {
            if (event.getOption("result").isPresent()) {
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
                    //return event.respondWithSuggestions(completes.getPlayerNames());
                }
            }
        }
        return event.respondWithSuggestions(suggestions);
    }

    public Mono<Message> buttons(ButtonInteractionEvent event) {

        List<Button> buttons = event.getInteraction().getMessage().map(message -> message.getComponents()).orElseGet(() -> List.of()).stream().filter(ActionRow.class::isInstance).map(ActionRow.class::cast).flatMap(actionRow -> actionRow.getChildren().stream()).filter(Button.class::isInstance).map(Button.class::cast).collect(Collectors.toList());
        Iterator butt = buttons.iterator();
        Button clicked = null;
        Member user = event.getInteraction().getMember().get();
        Button dangerButton = null;
        Button successButton = null;
        String challenged_team = "";
        while (butt.hasNext()) {
            Button button = (Button) butt.next();
            if (button.getCustomId().get().contains(event.getCustomId())) {
                challenged_team = button.getLabel().get();
                if (button.getStyle().getValue() == 4) {
                    clicked = Button.danger(button.getCustomId().get(), "Match set with: " + button.getLabel().get()).disabled();
                } else {
                    clicked = Button.success(button.getCustomId().get(), "Match set with: " + button.getLabel().get()).disabled();
                }
            }
        }

        int challenged_team_id = 0;
        try {
            PreparedStatement stTeamId = connection.prepareStatement("select team_id from teams where team_name like ?");
            stTeamId.setString(1, challenged_team);
            ResultSet rsTeamId = stTeamId.executeQuery();
            if (rsTeamId.next()) { // Assuming there is at least one row in the result set
                challenged_team_id = rsTeamId.getInt(1);
            }
            String team_name = "";
            int player_id = 0;
            int player_team_id = 0;
            try {
                PreparedStatement userQuery = connection.prepareStatement("select * from players where player_name like '" + user.getUsername() + "'");

                ResultSet rs = userQuery.executeQuery();
                ;
                while (rs.next()) {
                    player_id = rs.getInt("player_id");
                    team_name = rs.getString("current_team");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            "".equals(team_name);

            PreparedStatement player_team = connection.prepareStatement("select team_id from teams where team_name like ?");
            player_team.setString(1, team_name);
            ResultSet playerTeamResult = player_team.executeQuery();
            if (playerTeamResult.next()) { // Assuming there is at least one row in the result set
                player_team_id = playerTeamResult.getInt(1);
            }

            PreparedStatement stMatchesCount = connection.prepareStatement("insert into matches (team_one_id, team_two_id) values (?,?)");
            stMatchesCount.setInt(1, challenged_team_id);
            stMatchesCount.setInt(2, player_team_id);

            int row = stMatchesCount.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Mono<Message> edit = event.editReply()
                .withComponents(ActionRow.of(clicked));

        return event.deferEdit().then(edit);

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
        ResultSet rs = null;
        try {
            st = connection.createStatement();
            Boolean mem = false;
            Member user = event.getInteraction().getMember().get();
            Iterator<Role> userRoles = user.getRoles().toIterable().iterator();
            while (userRoles.hasNext()) {
                Role current = userRoles.next();
                if (current.getName().contains(LadderBot.tournament_role)) {
                    mem = true;
                    break;
                }
            }
            if (!mem) {
                return event.reply()
                        .withEphemeral(false).withContent("Sorry <@" + user.getId().asString() + "> you must be a tournament member to do this");
            }

            if (event.getOption("confront").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("confront").get();

                //Get team current user is a member of:
                //What's the users player_id as this will be different from their Discord Snowflake ID
                int player_id = 0;
                int team_id = 0;
                String team_name = "";
                try {
                    st.executeQuery("select * from players where player_name like '" + user.getUsername() + "'");
                    rs = st.getResultSet();
                    while (rs.next()) {
                        player_id = rs.getInt("player_id");
                        team_name = rs.getString("current_team");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if ("".equals(team_name)) {
                    return event.reply()
                            .withEphemeral(false).withContent("<@" + user.getId().asString() + ">, you can't challenge because you're not in a team!");
                }

                PreparedStatement stTeamId = connection.prepareStatement("select team_id from teams where team_name like ?");
                stTeamId.setString(1, team_name);
                ResultSet rsTeamId = stTeamId.executeQuery();
                if (rsTeamId.next()) { // Assuming there is at least one row in the result set
                    team_id = rsTeamId.getInt(1);
                }

//                //Get current team rank: SELECT rank FROM ladder WHERE team_id = current_team_id;
//                int team_rank = 0;
//                PreparedStatement ranks = connection.prepareStatement("SELECT rank FROM ladder WHERE team_id = ?");
//                ranks.setString(1, team_name);
//                ResultSet ranksResult = ranks.executeQuery();
//                if (ranksResult.next()) { // Assuming there is at least one row in the result set
//                    team_rank = rsTeamId.getInt(1);
//                }

                //Get next two 'highest': SELECT team_id
                //FROM ladder
                //WHERE rank < (SELECT rank FROM ladder WHERE team_id = current_team_id)
                //ORDER BY rank ASC
                //LIMIT 2;

                PreparedStatement ranks = connection.prepareStatement("SELECT team_id, rank\n"
                        + "                FROM ladder\n"
                        + "                WHERE rank < (SELECT rank FROM ladder WHERE team_id = ?)\n"
                        + "                ORDER BY rank DESC\n"
                        + "                LIMIT 2");
                ranks.setInt(1, team_id);
                LOGGER.debug("Rank query: " + ranks.toString());
                ResultSet ranksResult = ranks.executeQuery();
//                while (ranksResult.next()) { // Assuming there is at least one row in the result set
//                    LOGGER.debug("Closest rank teams: " + ranksResult.getInt(1));
//                }
                int hardest = 0;
                String hardest_name = "";
                int hardest_rank = 0;
                if (ranksResult.next()) {
                    hardest = ranksResult.getInt(1);
                    hardest_rank = ranksResult.getInt(2);
                    PreparedStatement hardestQuery = connection.prepareStatement("select team_name from teams where team_id=?");
                    hardestQuery.setInt(1, hardest);
                    ResultSet hardestResult = hardestQuery.executeQuery();
                    if (hardestResult.next()) { // Assuming there is at least one row in the result set
                        hardest_name = hardestResult.getString(1);

                    }
                }
                int easiest = 0;
                String easiest_name = "";
                int easiest_rank = 0;
                if (ranksResult.next()) {
                    easiest = ranksResult.getInt(1);
                    easiest_rank = ranksResult.getInt(2);
                    PreparedStatement easiestQuery = connection.prepareStatement("select team_name from teams where team_id=?");
                    easiestQuery.setInt(1, easiest);
                    ResultSet easiestResult = easiestQuery.executeQuery();
                    if (easiestResult.next()) { // Assuming there is at least one row in the result set
                        easiest_name = easiestResult.getString(1);
                    }
                }
                //I feel dirty for what I have just done.

                if (hardest == 0 && easiest == 0) {
                    return event.reply()
                            .withEphemeral(false).withContent("Hey  <@" + user.getId().asString() + ">, your team is the top of the ladder! Nobody to challenge :(");
                }
                //Replace dummy team data with info from the backend.
                //Need to get ladder data, current team location then one above is 'success' (green), two above is 'danger' (red)
                EmbedCreateSpec embed = EmbedCreateSpec.builder()
                        .color(Color.of(255, 153, 0))
                        .title("Select your challenge:")
                        .author("Challenge options", "", "https://cdn.discordapp.com/avatars/1198703676987023450/8d7ab02c29bf51ac5f7c70615a2c3afb.png")
                        .description(hardest_name + " (<@508675578229162004> and <@1198703676987023450>) \nor\n" + easiest_name + " (<@508675578229162004> and <@1198703676987023450>)")
                        .image("https://images-wixmp-ed30a86b8c4ca887773594c2.wixmp.com/f/3db49b4c-f1c1-4bb6-85db-28aef1446bfb/d7xv7ks-55352abf-cd58-487f-ba38-7310f84bdf01.jpg?token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1cm46YXBwOjdlMGQxODg5ODIyNjQzNzNhNWYwZDQxNWVhMGQyNmUwIiwiaXNzIjoidXJuOmFwcDo3ZTBkMTg4OTgyMjY0MzczYTVmMGQ0MTVlYTBkMjZlMCIsIm9iaiI6W1t7InBhdGgiOiJcL2ZcLzNkYjQ5YjRjLWYxYzEtNGJiNi04NWRiLTI4YWVmMTQ0NmJmYlwvZDd4djdrcy01NTM1MmFiZi1jZDU4LTQ4N2YtYmEzOC03MzEwZjg0YmRmMDEuanBnIn1dXSwiYXVkIjpbInVybjpzZXJ2aWNlOmZpbGUuZG93bmxvYWQiXX0.ojmjc2svRjM8ia-m5lZA7CU55VrLp-KMrRlcilW247I")
                        .build();

                EmbedCreateSpec.Builder dynamic = EmbedCreateSpec.builder().from(embed);
                dynamic.addField("", "", false);
                dynamic.addField("Team", "", true);
                dynamic.addField("Total games played", "", true);
                dynamic.addField("Ladder position", "", true);
                dynamic.addField("", hardest_name, true);
                dynamic.addField("", "10", true);
                dynamic.addField("", Integer.toString(hardest_rank), true);
                Button successButton = null;
                Button dangerButton = Button.danger("challenge-dangerButton-id" + UUID.randomUUID(), hardest_name);
                InteractionApplicationCommandCallbackSpec messageSpec = null;
                if (easiest != 0) {
                    successButton = Button.success("challenge-successButton-id" + UUID.randomUUID(), easiest_name);
                    dynamic.addField("", easiest_name, true);
                    dynamic.addField("", "8", true);
                    dynamic.addField("", Integer.toString(easiest_rank), true);
                }
                embed = dynamic.build();
                if (easiest != 0) {
                    messageSpec = InteractionApplicationCommandCallbackSpec.builder()
                            .addEmbed(embed)
                            .addComponent(ActionRow.of(dangerButton, successButton))
                            .build();
                } else {
                    messageSpec = InteractionApplicationCommandCallbackSpec.builder()
                            .addEmbed(embed)
                            .addComponent(ActionRow.of(dangerButton))
                            .build();
                }


                return event.reply(messageSpec);
            } else if (event.getOption("result").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("result").get();

                String team_name = type.getOption("team_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();
                String replay_id = type.getOption("replay_id").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();
                //Get player team details
                int player_id = 0;
                int player_team_id = 0;
                String player_team_name = "";
                try {
                    st.executeQuery("select * from players where player_name like '" + user.getUsername() + "'");
                    rs = st.getResultSet();
                    while (rs.next()) {
                        player_id = rs.getInt("player_id");
                        player_team_name = rs.getString("current_team");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if ("".equals(player_team_name)) {
                    return event.reply()
                            .withEphemeral(false).withContent("<@" + user.getId().asString() + ">, you can't challenge because you're not in a team!");
                }

                PreparedStatement stTeamId = connection.prepareStatement("select team_id from teams where team_name like ?");
                stTeamId.setString(1, player_team_name);
                ResultSet rsTeamId = stTeamId.executeQuery();
                if (rsTeamId.next()) { // Assuming there is at least one row in the result set
                    player_team_id = rsTeamId.getInt(1);
                }
                //Get winning team details
                int team_id = 0;
                PreparedStatement winner = connection.prepareStatement("select team_id from teams where team_name like ?");
                winner.setString(1, team_name);
                ResultSet winRS = winner.executeQuery();
                if (winRS.next()) { // Assuming there is at least one row in the result set
                    team_id = winRS.getInt(1);
                }

                int opponent_team_id = 0;
                //Need the opponent team id - replay_id = 0 and one of the teams has this id.
                PreparedStatement opponentQuery = connection.prepareStatement("SELECT \n"
                        + "    match_id,\n"
                        + "    CASE \n"
                        + "        WHEN team_one_id = ? THEN team_two_id\n"
                        + "        ELSE team_one_id\n"
                        + "    END AS opponent_team_id,\n"
                        + "    match_date\n"
                        + "FROM \n"
                        + "    matches\n"
                        + "WHERE \n"
                        + "    (team_one_id = ? OR team_two_id = ?)\n"
                        + "    AND replay_id = 0;\n");
                opponentQuery.setInt(1, team_id);
                opponentQuery.setInt(2, team_id);
                opponentQuery.setInt(3, team_id);
                ResultSet oppRS = opponentQuery.executeQuery();
                if (oppRS.next()) { // Assuming there is at least one row in the result set
                    opponent_team_id = oppRS.getInt("opponent_team_id");
                }

                //Time to manage the ladder changes.. eak.
                PreparedStatement stMatchesCount = connection.prepareStatement("UPDATE matches SET replay_id = ?, winner = ? WHERE (team_one_id = ? OR team_two_id = ?) AND replay_id = 0");
                stMatchesCount.setInt(1, Integer.parseInt(replay_id));
                stMatchesCount.setInt(2, team_id);
                stMatchesCount.setInt(3, team_id);
                stMatchesCount.setInt(4, team_id);
                System.out.println("Match update" + stMatchesCount.toString());
                int row = stMatchesCount.executeUpdate();
                if (player_team_id != team_id) {
                    LOGGER.debug("Bailing because player team lost, so there are no changes to make");
                    return event.reply()
                            .withEphemeral(false).withContent("<@" + user.getId().asString() + ">, sorry for your loss!");
                }

                //Get existing ranks:
                int team1_rank = 0, team2_rank = 0;
                PreparedStatement ranksQuery = connection.prepareStatement("select rank from ladder where team_id=?");
                ranksQuery.setInt(1, team_id);
                ResultSet rsTeamRankId = ranksQuery.executeQuery();
                if (rsTeamRankId.next()) { // Assuming there is at least one row in the result set
                    team1_rank = rsTeamRankId.getInt(1);
                }

                PreparedStatement ranksQuery2 = connection.prepareStatement("select rank from ladder where team_id=?");
                ranksQuery2.setInt(1, opponent_team_id);
                ResultSet rsTeamRankId2 = ranksQuery2.executeQuery();
                if (rsTeamRankId2.next()) { // Assuming there is at least one row in the result set
                    team2_rank = rsTeamRankId2.getInt(1);
                }

                LOGGER.debug("Ranks: " + team1_rank + " :: " + team2_rank);
                PreparedStatement ladderSwitch = connection.prepareStatement("UPDATE ladder set rank=? where team_id=?");
                ladderSwitch.setInt(1, team2_rank);
                ladderSwitch.setInt(2, team_id);
                LOGGER.debug(ladderSwitch.toString());
                int ladRow = ladderSwitch.executeUpdate();

                PreparedStatement ladderSwitch2 = connection.prepareStatement("UPDATE ladder set rank=? where team_id=?");
                ladderSwitch2.setInt(1, team1_rank);
                ladderSwitch2.setInt(2, opponent_team_id);
                LOGGER.debug(ladderSwitch2.toString());
                int ladRow2 = ladderSwitch2.executeUpdate();


                return event.reply()
                        .withEphemeral(false).withContent("Thanks  <@" + user.getId().asString() + ">, you just registered a victory for " + team_name);
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
                .withEphemeral(true).withContent("Challenge command failed, no valid option found");
    }


}
