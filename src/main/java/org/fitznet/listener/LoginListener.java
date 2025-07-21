package org.fitznet.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.fitznet.util.EmbedUtil;
import org.fitznet.util.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.fitznet.util.Constants.BOT_MESSAGE_CHANNEL_ID;
import static org.fitznet.util.Constants.TOTALLY_LEGIT_DATABASE_FILENAME;

@Slf4j
public class LoginListener extends ListenerAdapter {
    private final Map<Long, Long> serverVoiceJoinCount = new HashMap<>();
    private final int[] loginMilestones = {1, 100, 500, 1000, 2000, 5000};
    private final JDA JDA;

    public LoginListener(JDA jda) {
        this.JDA = jda;
        loadCountsFromFile();
    }

    private void loadCountsFromFile() {
        try {
            File file = new File(TOTALLY_LEGIT_DATABASE_FILENAME);
            if (file.exists() && file.length() > 0) {
                serverVoiceJoinCount.putAll(JsonUtils.MAPPER.readValue(file, new TypeReference<Map<Long, Long>>() {
                }));
            } else {
                boolean isNewFileCreated = file.createNewFile();
                log.info("New file created?: {}", isNewFileCreated);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private void saveCounts() {
        try {
            JsonUtils.MAPPER.writeValue(new File(TOTALLY_LEGIT_DATABASE_FILENAME), serverVoiceJoinCount);
        } catch (IOException e) {
            log.warn("Failed to save voice join counts to file", e);
        }
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {

        // Server Login Counter
        if (event.getChannelLeft() == null) {
            Member user = event.getMember();
            log.info("User {} has joined a {} voice channel {} time(s).", event.getGuild().getName(),
                    event.getMember().getEffectiveName(), serverVoiceJoinCount.get(user.getIdLong()));
            // S A F E !
            synchronized (serverVoiceJoinCount) {
                long count = serverVoiceJoinCount.getOrDefault(user.getIdLong(), 0L) + 1;
                serverVoiceJoinCount.put(user.getIdLong(), count);

                log.debug("Server Stats{}", serverVoiceJoinCount);
                for (int milestone : loginMilestones) {
                    if (count == milestone) {
                        TextChannel botChannel = JDA.getTextChannelById(BOT_MESSAGE_CHANNEL_ID);
                        if (botChannel != null) {
                            MessageEmbed embed = EmbedUtil.createMilestoneEmbed(event.getMember(), milestone);
                            botChannel.sendMessageEmbeds(embed)
                                    .queue(
                                            // success consumer
                                            message -> log.info("Milestone message sent successfully"),
                                            // failure consumer
                                            error -> log.error("Failed to send milestone message", error)
                                    );
                        } else {
                            log.error("BotChannel not found to post milestone results!");
                        }
                    }
                }
                saveCounts();
            }
        }
    }
}
