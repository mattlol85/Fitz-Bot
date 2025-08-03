package org.fitznet;

import lombok.extern.slf4j.Slf4j;
import org.fitznet.service.BotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/bot")
public class BotController {

    private final BotService botService;

    @Autowired
    public BotController(BotService botService) {
        this.botService = botService;
    }

    @PostMapping("/startup")
    public String startup() {
        return botService.startBot();
    }

    @PostMapping("/force-update-commands")
    public String forceUpdateCommands() {
        return botService.forceUpdateCommands();
    }

    @PostMapping("/register-guild-commands/{guildId}")
    public String registerGuildCommands(@PathVariable String guildId) {
        return botService.registerGuildCommands(guildId);
    }

    @PostMapping("/shutdown")
    public String shutdown() {
        return botService.stopBot();
    }

    @GetMapping("/status")
    public String getStatus() {
        return botService.getStatus();
    }

    @PostMapping("/reset-join-counts/{guildId}")
    public String resetJoinCounts(@PathVariable String guildId) {
        return botService.resetGuildJoinCounts(guildId);
    }
}
