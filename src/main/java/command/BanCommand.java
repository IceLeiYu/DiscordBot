package main.java.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import static main.java.BotSetting.noPermissionERROR;
import static main.java.SlashCommandOption.DAYS;
import static main.java.SlashCommandOption.USER_TAG;
import static main.java.util.EmbedUtil.createEmbed;
import static main.java.util.GuildUtil.guild;

public class BanCommand {
    public void onCommand(SlashCommandEvent event) {
        Member member = event.getMember();

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            event.replyEmbeds(createEmbed(noPermissionERROR, 0xFF0000)).setEphemeral(true).queue();
            return;
        }

        Member selfMember = guild.getSelfMember();
        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            event.replyEmbeds(createEmbed("機器人並沒有權限封禁成員", 0xFF0000)).setEphemeral(true).queue();
            return;
        }

        if (member != null && !selfMember.canInteract(member)) {
            event.replyEmbeds(createEmbed("此成員的力量大到讓我無法執行此動作", 0xFF0000)).setEphemeral(true).queue();
            return;
        }

        int delDays = 0;
        User user = event.getOption(USER_TAG).getAsUser();
        OptionMapping option = event.getOption(DAYS);
        if (option != null)
            delDays = (int) Math.max(0, Math.min(7, option.getAsLong()));
        guild.ban(user, delDays)
                .flatMap(v -> event.replyEmbeds(createEmbed("封禁成員 " + user.getAsTag(), 0xffb1b3)).setEphemeral(true))
                .queue();
    }
}
