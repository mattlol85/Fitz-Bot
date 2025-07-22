package org.fitznet.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.fitznet.listener.LoginListener;

/**
 * Utility class for creating and configuring JDA instances with consistent settings.
 */
public class JdaConfiguration {

    /**
     * Creates a JDA instance with standard configuration.
     * 
     * @param token The Discord bot token
     * @return A fully configured and ready JDA instance
     * @throws InterruptedException if the JDA build process is interrupted
     */
    public static JDA createConfiguredJda(String token) throws InterruptedException {
        JDA jda = JDABuilder.createDefault(token)
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.watching("The server... at all times"))
                .build().awaitReady();

        // Add the LoginListener after JDA is created and ready
        jda.addEventListener(new LoginListener(jda));

        return jda;
    }
}