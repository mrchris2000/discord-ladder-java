package com.github.mrchris2000.discordLadder.commands;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;

public class TeamCommand implements SlashCommand {
    public TeamCommand(Connection connection) {
        this.connection = connection;
    }

    @Override
    public String getName() {
        return "team";
    }

    private Connection connection;

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
            if (event.getOption("add").isPresent()) {
                ApplicationCommandInteractionOption type = event.getOption("add").get();

                String name = type.getOption("name").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .get();

                st = connection.prepareStatement("INSERT INTO teams (team_name) VALUES (?)");
                ((PreparedStatement) st).setString(1, name);
                int row = ((PreparedStatement) st).executeUpdate();
                return event.reply()
                        .withEphemeral(false).withContent("Adding team: " + name + " : Row : "+row);
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
                String name = type.getOption("name").flatMap(ApplicationCommandInteractionOption::getValue)
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

                return event.reply()
                        .withEphemeral(false).withContent("Team stats for " + team + " : --");
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