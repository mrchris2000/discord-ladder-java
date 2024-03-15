package com.github.mrchris2000.discordLadder.commands;

import com.github.mrchris2000.discordLadder.infra.AutoCompletes;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.component.*;
import discord4j.core.object.component.ActionComponent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.LayoutComponent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ChallengeCommand implements SlashCommand {
    public ChallengeCommand(Connection connection, AutoCompletes completes) {
        this.connection = connection;
        this.completes = completes;
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

    public Mono<Void> complete(ChatInputAutoCompleteEvent event) {
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

    public Mono<Message> buttons(ButtonInteractionEvent event) {

        List<Button> buttons = event.getInteraction().getMessage().map(message -> message.getComponents()).orElseGet(() -> List.of()).stream().filter(ActionRow.class::isInstance).map(ActionRow.class::cast).flatMap(actionRow -> actionRow.getChildren().stream()).filter(Button.class::isInstance).map(Button.class::cast).collect(Collectors.toList());
        Iterator butt = buttons.iterator();
        Button clicked = null;

        Button dangerButton = null;
        Button successButton = null;
        while (butt.hasNext()) {
            Button button = (Button) butt.next();
            button.disabled();
            if (button.getLabel().get().equals("Alpha Team")) {
                dangerButton = Button.danger(button.getCustomId().get(), "Alpha Team").disabled();
            }
            if (button.getLabel().get().equals("Bravo Team")) {
                successButton = Button.success(button.getCustomId().get(), "Bravo Team").disabled();
            }
            if (button.getCustomId().get().contains(event.getCustomId())) {
                System.out.println("Match!! " + button.getCustomId());
                clicked = button;
            }
        }
        Mono<Message> edit = event.editReply()
                .withComponents(ActionRow.of(dangerButton, successButton));
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
        try {
            st = connection.createStatement();

            //Replace dummy team data with info from the backend.
            EmbedCreateSpec embed = EmbedCreateSpec.builder()
                    .color(Color.of(255, 153, 0))
                    .title("Select your challenge:")
                    .author("Challenge options", "", "https://cdn.discordapp.com/avatars/1198703676987023450/8d7ab02c29bf51ac5f7c70615a2c3afb.png")
                    .description("Alpha Team (<@508675578229162004> and <@1198703676987023450>) \nor\n Bravo Team (<@508675578229162004> and <@1198703676987023450>)")
                    .addField("", "", false)
                    .addField("Team", "", true)
                    .addField("Total games played", "", true)
                    .addField("Ladder position", "", true)
                    .addField("", "Alpha Team", true)
                    .addField("", "10", true)
                    .addField("", "2", true)
                    .addField("", "Bravo Team", true)
                    .addField("", "8", true)
                    .addField("", "3", true)
                    .image("https://images-wixmp-ed30a86b8c4ca887773594c2.wixmp.com/f/3db49b4c-f1c1-4bb6-85db-28aef1446bfb/d7xv7ks-55352abf-cd58-487f-ba38-7310f84bdf01.jpg?token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1cm46YXBwOjdlMGQxODg5ODIyNjQzNzNhNWYwZDQxNWVhMGQyNmUwIiwiaXNzIjoidXJuOmFwcDo3ZTBkMTg4OTgyMjY0MzczYTVmMGQ0MTVlYTBkMjZlMCIsIm9iaiI6W1t7InBhdGgiOiJcL2ZcLzNkYjQ5YjRjLWYxYzEtNGJiNi04NWRiLTI4YWVmMTQ0NmJmYlwvZDd4djdrcy01NTM1MmFiZi1jZDU4LTQ4N2YtYmEzOC03MzEwZjg0YmRmMDEuanBnIn1dXSwiYXVkIjpbInVybjpzZXJ2aWNlOmZpbGUuZG93bmxvYWQiXX0.ojmjc2svRjM8ia-m5lZA7CU55VrLp-KMrRlcilW247I")
                    .build();

            Button dangerButton = Button.danger("challenge-dangerButton-id" + UUID.randomUUID(), "Alpha Team");
            Button successButton = Button.success("challenge-successButton-id" + UUID.randomUUID(), "Bravo Team");


            InteractionApplicationCommandCallbackSpec messageSpec = InteractionApplicationCommandCallbackSpec.builder()
                    .addEmbed(embed)
                    .addComponent(ActionRow.of(dangerButton, successButton))
                    .build();

            return event.reply(messageSpec);
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
