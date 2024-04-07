package com.github.mrchris2000.discordLadder.commands;

import com.github.mrchris2000.discordLadder.infra.AutoCompletes;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.*;
import discord4j.core.spec.*;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.*;
import java.time.format.*;
import java.util.*;

public class TeamCommand implements SlashCommand {
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
            }else if (event.getOption("stats").isPresent()) {
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

                String team = type.getOption("team").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                LocalDate date = LocalDate.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy MM dd");
                String text = date.format(formatter);
                LocalDate parsedDate = LocalDate.parse(text, formatter);

                EmbedCreateSpec embed = EmbedCreateSpec.builder()
                        .color(Color.BLUE)
                        .title(team)
                        .url("https://discord4j.com")
                        .author("Team stats", "https://discord4j.com", "https://cdn.discordapp.com/avatars/1198703676987023450/8d7ab02c29bf51ac5f7c70615a2c3afb.png")
                        .description("<@508675578229162004> and <@1198703676987023450>")
                        .thumbnail("https://cdn.discordapp.com/avatars/1198703676987023450/8d7ab02c29bf51ac5f7c70615a2c3afb.png?size=256")
                        .addField("Total games played", "value", true)
                        .addField("Ladder position", "3", true)
                        .addField("\u200B", "\u200B", false)
                        .addField("Played", "other team", true)
                        .addField("On", parsedDate.toString(), true)
                        .addField("Result", "Draw", true)
                        .image("https://images-wixmp-ed30a86b8c4ca887773594c2.wixmp.com/f/2da776de-a93a-4c96-92bf-1bc81981d4c5/d9444o9-521d0b63-e19a-4f4f-807e-4f12a77544b9.png/v1/fill/w_1024,h_576,q_80,strp/supreme_commander_forged_alliance_faf_flat_by_joyden_d9444o9-fullview.jpg?token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1cm46YXBwOjdlMGQxODg5ODIyNjQzNzNhNWYwZDQxNWVhMGQyNmUwIiwiaXNzIjoidXJuOmFwcDo3ZTBkMTg4OTgyMjY0MzczYTVmMGQ0MTVlYTBkMjZlMCIsIm9iaiI6W1t7ImhlaWdodCI6Ijw9NTc2IiwicGF0aCI6IlwvZlwvMmRhNzc2ZGUtYTkzYS00Yzk2LTkyYmYtMWJjODE5ODFkNGM1XC9kOTQ0NG85LTUyMWQwYjYzLWUxOWEtNGY0Zi04MDdlLTRmMTJhNzc1NDRiOS5wbmciLCJ3aWR0aCI6Ijw9MTAyNCJ9XV0sImF1ZCI6WyJ1cm46c2VydmljZTppbWFnZS5vcGVyYXRpb25zIl19.5GQcNvC_dhl2wqEuXjbRvj-05fI3MMRTs6vvzkdThJY")
                        .build();


                return event.reply()
                        .withEphemeral(false).withEmbeds(embed);
            } else if (event.getOption("join").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("join").get();

                String team_name = type.getOption("team_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                //What's the users player_id as this will be different from their Discord Snowflake ID
                String player_id = "";
                try {
                    st.executeQuery("select player_id from players where player_name like '" + user.getUsername() + "'");
                    rs = st.getResultSet();
                    while (rs.next()) {
                        player_id = player_id.concat(rs.getString("player_id"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
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
                if(player1_id == Integer.parseInt(player_id)){
                    return event.reply()
                            .withEphemeral(false).withContent("Hey  <@" + user.getId().asString() + ">, you're already a member of' " + team_name);
                } else if(player2_id == Integer.parseInt(player_id)){
                    return event.reply()
                            .withEphemeral(false).withContent("Hey  <@" + user.getId().asString() + ">, you're already a member of' " + team_name);
                }

                //Add either p1 or p2
                st = connection.prepareStatement("UPDATE teams SET player_" + playerNumber + "_id = ? WHERE team_name = ?");
                ((PreparedStatement) st).setInt(1, Integer.parseInt(player_id));
                ((PreparedStatement) st).setString(2, team_name);
                int row = ((PreparedStatement) st).executeUpdate();

                return event.reply()
                        .withEphemeral(false).withContent("Congratulations  <@" + user.getId().asString() + ">, you just joined " + team_name);
            } else if (event.getOption("leave").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("leave").get();

                String team_name = type.getOption("team_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                Mono<User> player_name = type.getOption("player_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asUser)
                        .get();

                String player_id = "";
                try {
                    st.executeQuery("select player_id from players where player_name like '" + player_name.block().getUsername() + "'");
                    rs = st.getResultSet();
                    while (rs.next()) {
                        player_id = player_id.concat(rs.getString("player_id"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                st = connection.prepareStatement("DELETE FROM teams where team_name=? and player_one_id=?");
                ((PreparedStatement) st).setString(1, team_name);
                ((PreparedStatement) st).setInt(2, Integer.parseInt(player_id));
                int row = ((PreparedStatement) st).executeUpdate();

                return event.reply()
                        .withEphemeral(false).withContent(player_name + " left team: " + team_name);
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
