package com.github.mrchris2000.discordLadder.commands;
import com.github.mrchris2000.discordLadder.infra.AutoCompletes;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.core.spec.*;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

import java.time.format.*;
import java.time.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

public class TeamCommand implements SlashCommand {
    public TeamCommand(Connection connection, AutoCompletes completes) {
        this.connection = connection;
        this.completes = completes;
    }

    @Override
    public String getName() {
        return "team";
    }

    private Connection connection;

    private AutoCompletes completes;

    public Mono<Void> complete(ChatInputAutoCompleteEvent event){
        Statement st = null;
        ResultSet rs = null;
        List<ApplicationCommandOptionChoiceData> suggestions = new ArrayList<>();
        if (event.getCommandName().equals("team")) {
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
                String option = type.getOptions().get(type.getOptions().size() -1).getName();
                if(option.equals("team_name"))
                    return event.respondWithSuggestions(completes.getTeamNames());
                else
                    return event.respondWithSuggestions(completes.getPlayerNames());
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
        ResultSet rs = null;
        try {
            st = connection.createStatement();
            if (event.getOption("create").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("create").get();

                String name = type.getOption("team_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                st = connection.prepareStatement("INSERT INTO teams (team_name) VALUES (?)");
                ((PreparedStatement) st).setString(1, name);
                int row = ((PreparedStatement) st).executeUpdate();

                return event.reply()
                        .withEphemeral(false).withContent("<@508675578229162004> Added team: " + name);
            } else if (event.getOption("list").isPresent()) {

                st.executeQuery("select team_name from teams");
                rs = st.getResultSet();
                String output = "Current teams:\n";
                while(rs.next()){
                    output = output.concat( rs.getString("team_name")+ "\n");
                }


                return event.reply()
                        .withEphemeral(false).withContent(output);
            } else if (event.getOption("remove").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("remove").get();
                String name = type.getOption("team_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                st = connection.prepareStatement("DELETE FROM teams WHERE team_name=?");
                ((PreparedStatement) st).setString(1, name);
                int row = ((PreparedStatement) st).executeUpdate();

                return event.reply()
                        .withEphemeral(false).withContent("Removing team: " + name);
            } else if (event.getOption("stats").isPresent()) {
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
            } else if (event.getOption("add").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("add").get();

                String team_name = type.getOption("team_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                String player_name = type.getOption("player_name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                st = connection.prepareStatement("INSERT INTO teams (team_name, player_one_id) VALUES (?,?)");
                ((PreparedStatement) st).setString(1, team_name);
                ((PreparedStatement) st).setString(2, player_name);
                int row = ((PreparedStatement) st).executeUpdate();

                return event.reply()
                        .withEphemeral(false).withContent(player_name + " added to team: " + team_name);
            }
        }catch(Exception e){
            e.printStackTrace();
        } finally {
            try{
                if(rs != null)
                    rs.close();
                if(st != null)
                    st.close();
            }catch (Exception e){
                //Well this is fucked then...
                e.printStackTrace();
            }
        }
        return  event.reply()
                .withEphemeral(true).withContent("Team command failed, no valid option found");
    }


}