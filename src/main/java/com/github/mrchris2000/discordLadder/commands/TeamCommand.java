package com.github.mrchris2000.discordLadder.commands;

import com.github.mrchris2000.discordLadder.infra.AutoCompletes;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.Embed;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.*;
import discord4j.core.spec.*;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

import java.sql.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

public class TeamCommand implements SlashCommand {

    private EmbedCreateSpec embedCreateSpec;

    public TeamCommand(Connection connection, AutoCompletes completes, Guild guild, Logger LOGGER) {
        this.connection = connection;
        this.completes = completes;
        this.guild = guild;
        this.LOGGER = LOGGER;
    }

    @Override
    public String getName() {
        return "team";
    }

    private final Connection connection;

    private final AutoCompletes completes;

    private final Guild guild;

    private final Logger LOGGER;

    public Mono<Void> complete(ChatInputAutoCompleteEvent event) {
        Statement st = null;
        ResultSet rs = null;
        List<ApplicationCommandOptionChoiceData> suggestions = new ArrayList<>();
        if ("team".equals(event.getCommandName())) {
            if (event.getOption("create").isPresent()) {
                return event.respondWithSuggestions(completes.getTeamNames());
            } else if (event.getOption("remove").isPresent()) {
                return event.respondWithSuggestions(completes.getTeamNames());
            } else if (event.getOption("join").isPresent()) {
                return event.respondWithSuggestions(completes.getTeamNames());
            } else if (event.getOption("leave").isPresent()) {
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
                }
                //else
                    //return event.respondWithSuggestions(completes.getPlayerNames());
            }
        }
        return event.respondWithSuggestions(suggestions);
    }

    public Mono<Message> buttons(ButtonInteractionEvent event) {
        return null;
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        Statement st = null;
        ResultSet rs = null;
        try {
            Boolean mem = false;
            Member user = event.getInteraction().getMember().get();
            Iterator<Role> userRoles = user.getRoles().toIterable().iterator();
            while (userRoles.hasNext()) {
                Role current = userRoles.next();
                if(current.getName().contains("2v2 Participant")){
                    mem = true;
                    break;
                }
            }
            if(!mem){
                return event.reply()
                        .withEphemeral(false).withContent("Sorry <@" + user.getId().asString() + "> you must be a tournament member to do this");
            }
            st = connection.createStatement();
            if (event.getOption("create").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("create").get();

                String name = type.getOption("team_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                //ToDo: We don't want duplicates..
                st = connection.prepareStatement("INSERT INTO teams (team_name) VALUES (?)");
                ((PreparedStatement) st).setString(1, name);
                int row = ((PreparedStatement) st).executeUpdate();

                return event.reply()
                        .withEphemeral(false).withContent("<@" + user.getId().asString() + "> added team: " + name);
            } else if (event.getOption("remove").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("remove").get();
                String name = type.getOption("team_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                //ToDo: Check team is empty before removal.
                st = connection.prepareStatement("DELETE FROM teams WHERE team_name=?");
                ((PreparedStatement) st).setString(1, name);
                int row = ((PreparedStatement) st).executeUpdate();

                //ToDo: Condense ladder up when team is removed

                return event.reply()
                        .withEphemeral(false).withContent("<@" + user.getId().asString() + "> removed team: " + name);
            } else if (event.getOption("list").isPresent()) {

                st.executeQuery("select team_name from teams");
                rs = st.getResultSet();
                String output = "Current teams:\n";
                while (rs.next()) {
                    output = output.concat(rs.getString("team_name") + "\n");
                }


                return event.reply()
                        .withEphemeral(false).withContent(output);
            }  else if (event.getOption("stats").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("stats").get();

                String team_name = type.getOption("team").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                EmbedCreateSpec embed = null;
                LocalDate date = LocalDate.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy MM dd");
                String text = date.format(formatter);
                LocalDate parsedDate = LocalDate.parse(text, formatter);
                String[] playerIDs = {"",""};
                String ladderPos = "\u200B";
                int team_id = 0;
                String matches = "";
                try {
                    PreparedStatement stTeamId = connection.prepareStatement("select team_id from teams where team_name like ?");
                    stTeamId.setString(1, team_name);
                    ResultSet rsTeamId = stTeamId.executeQuery();
                    if (rsTeamId.next()) { // Assuming there is at least one row in the result set
                        team_id = rsTeamId.getInt(1);
                    }

                    PreparedStatement stPlayerIds = connection.prepareStatement("select discord_id from players where current_team like ?");
                    stPlayerIds.setString(1, team_name);
                    rs = stPlayerIds.executeQuery();
                    int i = 0;
                    while (rs.next()) {
                        playerIDs[i] = "<@" + (rs.getString("discord_id")) + ">";
                        i++;
                    }
                    if(playerIDs[1].equals("")){
                        playerIDs[1] = "Missing team mate :( ";
                    }
                    LOGGER.debug("Team ID: "+team_id);
                    PreparedStatement stMatchesCount = connection.prepareStatement("SELECT COUNT(*) from matches where team_one_id = ? OR team_two_id = ?");
                    stMatchesCount.setInt(1, team_id);
                    stMatchesCount.setInt(2, team_id);
                    ResultSet rsMatches = stMatchesCount.executeQuery();
                    if (rsMatches.next()) { // Assuming there is at least one row in the result set
                        matches = String.valueOf(rsMatches.getInt(1));
                    } else {
                        matches = "No matches found";
                    }
                    PreparedStatement stLadderPos = connection.prepareStatement("SELECT rank from ladder where team_id = ?");
                    stLadderPos.setInt(1, team_id);
                    ResultSet rsLadder = stLadderPos.executeQuery();
                    if (rsMatches.next()) { // Assuming there is at least one row in the result set
                        ladderPos = String.valueOf(rsMatches.getInt(1));
                    } else {
                        ladderPos = "Unranked";
                    }


                    embed = EmbedCreateSpec.builder()
                            .color(Color.BLUE)
                            .title(team_name)
                            .url("https://discord4j.com")
                            .author("Team stats", "https://discord4j.com", "https://cdn.discordapp.com/avatars/1198703676987023450/8d7ab02c29bf51ac5f7c70615a2c3afb.png")
                            .description( playerIDs[0] + " and " + playerIDs[1] + "")
                            .thumbnail("https://cdn.discordapp.com/avatars/1198703676987023450/8d7ab02c29bf51ac5f7c70615a2c3afb.png?size=256")
                            .addField("Total games played", matches, true)
                            .addField("Ladder position", ladderPos, true)
                            .addField("\u200B", "\u200B", false)
                            .image("https://images-wixmp-ed30a86b8c4ca887773594c2.wixmp.com/f/2da776de-a93a-4c96-92bf-1bc81981d4c5/d9444o9-521d0b63-e19a-4f4f-807e-4f12a77544b9.png/v1/fill/w_1024,h_576,q_80,strp/supreme_commander_forged_alliance_faf_flat_by_joyden_d9444o9-fullview.jpg?token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1cm46YXBwOjdlMGQxODg5ODIyNjQzNzNhNWYwZDQxNWVhMGQyNmUwIiwiaXNzIjoidXJuOmFwcDo3ZTBkMTg4OTgyMjY0MzczYTVmMGQ0MTVlYTBkMjZlMCIsIm9iaiI6W1t7ImhlaWdodCI6Ijw9NTc2IiwicGF0aCI6IlwvZlwvMmRhNzc2ZGUtYTkzYS00Yzk2LTkyYmYtMWJjODE5ODFkNGM1XC9kOTQ0NG85LTUyMWQwYjYzLWUxOWEtNGY0Zi04MDdlLTRmMTJhNzc1NDRiOS5wbmciLCJ3aWR0aCI6Ijw9MTAyNCJ9XV0sImF1ZCI6WyJ1cm46c2VydmljZTppbWFnZS5vcGVyYXRpb25zIl19.5GQcNvC_dhl2wqEuXjbRvj-05fI3MMRTs6vvzkdThJY")
                            .build();


                    EmbedCreateSpec.Builder dynamic = EmbedCreateSpec.builder().from(embed);
                    PreparedStatement matchDetails = connection.prepareStatement("WITH TeamMatches AS (\n" +
                            "    SELECT\n" +
                            "        m.match_id,\n" +
                            "        CASE\n" +
                            "            WHEN t.team_id = m.team_one_id THEN m.team_two_id\n" +
                            "            ELSE m.team_one_id\n" +
                            "        END AS opposition_id,\n" +
                            "        TO_CHAR(m.match_date, 'DD-MM-YYYY HH24:MI') AS formatted_match_date,\n" +
                            "        t.team_name AS team_name,\n" +
                            "        m.winner\n" +
                            "    FROM\n" +
                            "        matches m\n" +
                            "    JOIN teams t ON t.team_id = m.team_one_id OR t.team_id = m.team_two_id\n" +
                            "    WHERE\n" +
                            "        t.team_name = ?\n" +
                            ")\n" +
                            "SELECT\n" +
                            "    tm.match_id,\n" +
                            "    tm.formatted_match_date,\n" +
                            "    tm.team_name,\n" +
                            "    opp.team_name AS opposition_name,\n" +
                            "    win.team_name AS winner_name\n" +
                            "FROM\n" +
                            "    TeamMatches tm\n" +
                            "JOIN teams opp ON tm.opposition_id = opp.team_id\n" +
                            "JOIN teams win ON tm.winner = win.team_id\n" +
                            "ORDER BY\n" +
                            "    tm.formatted_match_date DESC\n" +
                            "LIMIT 5");
                    matchDetails.setString(1, team_name);

                    ResultSet rsMatchDetails = matchDetails.executeQuery();
                    if(!matches.equals("0")) {
                        LOGGER.debug("Rows: " + matches);
                        String opposition = "";

                        rsMatchDetails.next();
                        String outcome = "";
                        dynamic.addField("Played", rsMatchDetails.getString("opposition_name"), true);
                        dynamic.addField("On", rsMatchDetails.getString("formatted_match_date"), true);
                        if (!rsMatchDetails.getString("winner_name").equals(team_name)) {
                            outcome = "Loss";
                        } else {
                            outcome = "Win";
                        }
                        dynamic.addField("Result", outcome, true);
                        while (rsMatchDetails.next()) {
                            dynamic.addField("\u200B", rsMatchDetails.getString("opposition_name"), true);
                            dynamic.addField("\u200B", rsMatchDetails.getString("formatted_match_date"), true);
                            if (!rsMatchDetails.getString("winner_name").equals(team_name)) {
                                outcome = "Loss";
                            } else {
                                outcome = "Win";
                            }
                            dynamic.addField("Result", outcome, true);
                        }
                    }
                    embed = dynamic.build();

                } catch (Exception e) {
                    e.printStackTrace();
                }

                return event.reply()
                        .withEphemeral(false).withEmbeds(embed);
            } else if (event.getOption("join").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("join").get();

                LOGGER.debug("Trying to join team");
                String team_name = type.getOption("team_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                //What's the users player_id as this will be different from their Discord Snowflake ID
                int player_id = 0;
                String current_team = "";
                try {
                    st.executeQuery("select * from players where player_name like '" + user.getUsername() + "'");
                    rs = st.getResultSet();
                    while (rs.next()) {
                        player_id = rs.getInt("player_id");
                        current_team = rs.getString("current_team");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if(current_team == null){
                    current_team = "";
                }

                //Should probably have a team class... can't be arsed, do that later.
                int player1_id=0;
                int player2_id=0;
                String playerNumber = "one";
                //Get existing team details
                try {
                    st.executeQuery("select * from teams where team_name='"+team_name+"'");
                    rs = st.getResultSet();
                    while (rs.next()) {
                        player1_id = rs.getInt("player_one_id");
                        if(player1_id != 0){
                            playerNumber = "two";
                        }
                        player2_id = rs.getInt("player_two_id");
                        if(player2_id != 0 ){
                            playerNumber = "full";
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //Is team full
                if(playerNumber.equals("full")){
                    return event.reply()
                            .withEphemeral(false).withContent("Sorry <@" + user.getId().asString() + "> team "+ team_name +" is currently full.");
                }
                //Player is already on the team?
                if(player1_id == player_id){
                    return event.reply()
                            .withEphemeral(false).withContent("Hey  <@" + user.getId().asString() + ">, you're already a member of' " + team_name);
                } else if(player2_id == player_id){
                    return event.reply()
                            .withEphemeral(false).withContent("Hey  <@" + user.getId().asString() + ">, you're already a member of' " + team_name);
                } else if(!current_team.equals("")){
                    return event.reply()
                            .withEphemeral(false).withContent("Hey  <@" + user.getId().asString() + ">, you're already a member of' " + current_team);
                }


                //Add either p1 or p2
                st = connection.prepareStatement("UPDATE teams SET player_" + playerNumber + "_id = ? WHERE team_name = ?");
                ((PreparedStatement) st).setInt(1, player_id);
                ((PreparedStatement) st).setString(2, team_name);
                int teamrow = ((PreparedStatement) st).executeUpdate();

                st = connection.prepareStatement("UPDATE players SET current_team=? WHERE player_id = ?");
                ((PreparedStatement) st).setInt(2, player_id);
                ((PreparedStatement) st).setString(1, team_name);
                int playerrow = ((PreparedStatement) st).executeUpdate();

                return event.reply()
                        .withEphemeral(false).withContent("Congratulations  <@" + user.getId().asString() + ">, you just joined " + team_name);
            } else if (event.getOption("leave").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("leave").get();

                String team_name = type.getOption("team_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                //What's the users player_id as this will be different from their Discord Snowflake ID
                int player_id = 0;
                String current_team = "";
                try {
                    st.executeQuery("select * from players where player_name like '" + user.getUsername() + "'");
                    rs = st.getResultSet();
                    while (rs.next()) {
                        player_id = rs.getInt("player_id");
                        current_team = rs.getString("current_team");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if(current_team == null){
                    current_team = "";
                } else if(!current_team.equals(team_name)){
                    return event.reply()
                            .withEphemeral(false).withContent("<@" + user.getId().asString() + ">, you can't leave a team you're not a member of!");
                }

                //Should probably have a team class... can't be arsed, do that later.
                int player1_id=0;
                int player2_id=0;
                String playerNumber = "one";
                //Get existing team details
                try {
                    st.executeQuery("select * from teams where team_name='"+team_name+"'");
                    rs = st.getResultSet();
                    while (rs.next()) {
                        player1_id = rs.getInt("player_one_id");
                        if(player1_id == player_id){
                            st.executeUpdate("UPDATE teams SET player_one_id=null where player_one_id='"+player_id+"'");
                            st.executeUpdate("UPDATE players SET current_team=null where player_id='"+player_id+"'");
                            break;
                        }
                        player2_id = rs.getInt("player_two_id");
                        if(player2_id == player_id ){
                            st.executeUpdate("UPDATE teams SET player_two_id=null where player_two_id='"+player_id+"'");
                            st.executeUpdate("UPDATE players SET current_team=null where player_id='"+player_id+"'");
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return event.reply()
                        .withEphemeral(false).withContent("Oh dear,  <@" + user.getId().asString() + ">, you just left " + team_name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
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
