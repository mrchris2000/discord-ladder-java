package com.github.mrchris2000.discordLadder.infra;

import com.github.mrchris2000.discordLadder.LadderBot;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

public class AutoCompletes {

    Connection connection;
    GatewayDiscordClient client;
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(LadderBot.class);

    public AutoCompletes(GatewayDiscordClient client, Connection connection) {
        this.client = client;
        this.connection = connection;
    }

    //Register players to the collection of players that are included
    public boolean addPlayers() {
        Iterator<Guild> guilds = client.getGuilds().toIterable().iterator();
        while (guilds.hasNext())
        {
            Guild guild = guilds.next();

            LOGGER.debug("Guild: " + guild.getName() + " : " + guild.getId());
            Iterator<Role> roles = guild.getRoles().toIterable().iterator();
            while (roles.hasNext()) {
                Role role = roles.next();
                LOGGER.debug("" + role.getName() + " : " + role.getId());
            }

            Iterator<Member> members = guild.getMembers().toIterable().iterator();
            while (members.hasNext()) {
                Member member = members.next();
                LOGGER.debug("Members: " + member.getDisplayName() + " : " + member.getId());
                Iterator<Role> memberRoles = guild.getRoles().toIterable().iterator();
                while (memberRoles.hasNext()) {
                    Role memberRole = memberRoles.next();
                    LOGGER.debug("Member: " + member.getDisplayName() + " : Role: " + memberRole.getName() + " : RoleID:" + memberRole.getId());
                    if ("1194596619253981204".equals(memberRole.getId().asString())) {
                        addPlayerToDB(member);
                        LOGGER.warn("Member has required role. Add to DB :)");
                    }
                }
            }
        }
        return false;
    }

    private void addPlayerToDB(Member member) {
        Statement st = null;
        try {
            st = connection.createStatement();
            st = connection.prepareStatement("INSERT INTO players (player_name, discord_id, active) VALUES (?,?,?)");
            ((PreparedStatement) st).setString(1, member.getDisplayName());
            ((PreparedStatement) st).setString(2, member.getId().asString());
            ((PreparedStatement) st).setBoolean(3, true);
            int row = ((PreparedStatement) st).executeUpdate();
            LOGGER.debug("Adding user - rows created:" + row);
            st.close();
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
    }

    public List<ApplicationCommandOptionChoiceData> getPlayerNames() {
        List<ApplicationCommandOptionChoiceData> suggestions = new ArrayList<>();
        Statement st = null;
        ResultSet rs = null;
        try {
            st = connection.createStatement();
            st.executeQuery("select player_name from players where active=TRUE");
            rs = st.getResultSet();
            String output = "";
            while (rs.next()) {
                LOGGER.debug("Adding player to suggestions: " + rs.getString("player_name"));
                suggestions.add(ApplicationCommandOptionChoiceData.builder().name(rs.getString("player_name")).value(rs.getString("player_name")).build());
            }
            return suggestions;
        } catch (Exception e) {
            e.printStackTrace();
            return suggestions;
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
    }

    public List<ApplicationCommandOptionChoiceData> getTeamNames() {
        List<ApplicationCommandOptionChoiceData> suggestions = new ArrayList<>();
        Statement st = null;
        ResultSet rs = null;
        try {
            st = connection.createStatement();
            st.executeQuery("select team_name from teams");
            rs = st.getResultSet();
            String output = "";
            while (rs.next()) {
                LOGGER.debug("Adding team to suggestions: " + rs.getString("team_name"));
                suggestions.add(ApplicationCommandOptionChoiceData.builder().name(rs.getString("team_name")).value(rs.getString("team_name")).build());
            }
            return suggestions;
        } catch (Exception e) {
            e.printStackTrace();
            return suggestions;
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
    }
}
