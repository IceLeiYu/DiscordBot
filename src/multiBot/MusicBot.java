package multiBot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import multiBot.music.GuildMusicManager;
import multiBot.music.TrackScheduler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static main.java.BotSetting.apiKEY;
import static main.java.util.Funtions.createEmbed;
import static multiBot.music.TrackScheduler.getUrlData;

public class MusicBot {
    private final String TAG;
    private final JDA jda;
    private final String botID;
    private final GuildMusicManager.Event event;
    private final MultiMusicBotManager musicBotManager;


    // music
    private final AudioPlayerManager playerManager;
    public Map<String, GuildMusicManager> musicManagers;

    public MusicBot(JDA jda, MultiMusicBotManager musicBotManager, GuildMusicManager.Event event) {
        this.TAG = "[" + jda.getSelfUser().getName() + "]";
        this.jda = jda;
        this.botID = jda.getSelfUser().getId();
        this.event = event;
        this.musicBotManager = musicBotManager;

        this.musicManagers = new HashMap<>();
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }


    /**
     * command player control
     */
    private void play(AudioPlaylist playlist, VoiceChannel vc, GuildMusicManager manager, GenericInteractionCreateEvent event) {
        connectVC(manager.guild, vc);
        manager.scheduler.addPlayListToQueue(playlist, event, this);
    }

    private void play(AudioTrack track, VoiceChannel vc, GuildMusicManager manager, GenericInteractionCreateEvent event, boolean searchAble) {
        connectVC(manager.guild, vc);
        manager.scheduler.queue(track, event, this, -1, searchAble);
    }

    public void changeVolume(int volume, Guild guild, SlashCommandEvent event) {
        getMusicManager(guild).scheduler.changeVolume(volume, event);
    }

    public void nextTrack(SlashCommandEvent event) {
        getMusicManager(event.getGuild()).scheduler.nextTrack(event);
    }

    public void playPrevious(SlashCommandEvent event) {
        Guild guild = jda.getGuildById(event.getGuild().getId());
        connectVC(guild, event.getMember().getVoiceState().getChannel());

        getMusicManager(event.getGuild()).scheduler.previousTrack(event);
    }

    public void toggleRepeat(SlashCommandEvent event) {
        getMusicManager(event.getGuild()).scheduler.toggleRepeat(event);
    }

    public void pause(SlashCommandEvent event, Guild guild, boolean play) {
        getMusicManager(guild).scheduler.pause(event, play);
    }

    public void loadAndPlay(final GenericInteractionCreateEvent event, final String trackUrl, boolean searchAble) {
        VoiceChannel vc = event.getMember().getVoiceState().getChannel();
        GuildMusicManager manager = getMusicManager(jda.getGuildById(event.getGuild().getId()));

        // 取得音樂
        playerManager.loadItemOrdered(musicManagers, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                play(track, vc, manager, event, searchAble);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                play(playlist, vc, manager, event);
            }

            @Override
            public void noMatches() {
                event.getHook().editOriginalEmbeds(createEmbed("查無此網址: " + trackUrl, 0xFF0000)).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                event.getHook().editOriginalEmbeds(createEmbed("無法播放此網址: " + exception.getMessage(), 0xFF0000)).queue();
            }
        });

    }

    public void displayQueue(GenericInteractionCreateEvent event, boolean searchAble) {
        TrackScheduler scheduler = getMusicManager(event.getGuild()).scheduler;
        if (scheduler.playingTrack == null) {
            event.getHook().editOriginalEmbeds(createEmbed("目前無音樂播放", 0xFF0000)).queue();
            return;
        }

        MessageEmbed[] embed = playStatus(event.getMember(), scheduler);

        if (searchAble)
            event.replyEmbeds(embed[0], embed[1]).addActionRows(controlButtons(event.getMember().getId(), scheduler.musicPause, scheduler.loopStatus)).setEphemeral(true).queue();
        else
            event.getHook().editOriginalComponents().setEmbeds(embed[0], embed[1])
                    .setActionRows(controlButtons(event.getMember().getId(), scheduler.musicPause, scheduler.loopStatus))
                    .queue();
    }

    /**
     * display
     */

    public MessageEmbed[] playStatus(Member member, TrackScheduler scheduler) {
        // 憲政播放資料
        StringBuilder progress = new StringBuilder();
        MessageEmbed nowPlaying;
        // 有歌曲正在播放
        if (scheduler.playingTrack != null) {
            // 進度顯示
            AudioTrackInfo trackInfo = scheduler.playingTrack.getInfo();
            int nowPlayingTime = (int) ((System.currentTimeMillis() - scheduler.startPlayTime) / 1000);
            int playPercent = (int) ((nowPlayingTime / (float) (trackInfo.length / 1000)) * 15);


            JSONObject urlInfo = new JSONObject(getUrlData("https://www.googleapis.com/youtube/v3/videos?id=" +
                    trackInfo.identifier + "&key=" + apiKEY + "&part=statistics,snippet")).getJSONArray("items").getJSONObject(0);
            JSONObject statistics = urlInfo.getJSONObject("statistics");

            progress.append("\n\n**[")
                    .append(timeCalculator(nowPlayingTime))
                    .append("] **").append("━".repeat(playPercent))
                    .append("❚")
                    .append("─".repeat(15 - playPercent))
                    .append("** [").append(trackInfo.isStream ? "LIVE" : (timeCalculator((int) (trackInfo.length / 1000))))
                    .append("]**\n");

            // 音量顯示
            int volumePercent = (int) (getMusicManager(member.getGuild()).player.getVolume() / 5f);
            progress.append("\n")
                    .append("**音量: **")
                    .append("◆".repeat(volumePercent))
                    .append("◇".repeat(20 - volumePercent))
                    .append(scheduler.loopStatus == 0 ? " <順序播放>\n" : (scheduler.loopStatus == 1 ? " <循環播放>\n" : " <單曲循環>\n"));

            // 組裝
            nowPlaying = createEmbed("**" + trackInfo.title + "**", trackInfo.uri,
                    progress.toString(),
                    new StringBuilder()
                            .append(" \uD83D\uDC40 ")
                            .append(String.format("%,d", Long.parseLong(statistics.getString("viewCount"))))
                            .append(" | \uD83D\uDC4D ").append(String.format("%,d", Long.parseLong(statistics.getString("likeCount"))))
                            .append(" | \uD83D\uDC4E ").append(String.format("%,d", Long.parseLong(statistics.getString("dislikeCount"))))
                            .append(" | \uD83D\uDCAC ").append(String.format("%,d", Long.parseLong(statistics.getString("commentCount"))))
                            .append(" | \uD83D\uDD0A").append(String.format("%.2f db", scheduler.loudness)).toString()

                    , trackInfo.author, urlInfo.getJSONObject("snippet").getJSONObject("thumbnails").getJSONObject("default").getString("url"),
                    0xe5b849);
        } else {
            nowPlaying = createEmbed(0xFF0000, "**[沒有歌曲正在被播放]**");
        }
        // 歌曲列表
        List<MessageEmbed.Field> fields = new ArrayList<>();
        if (scheduler.getQueue().size() == 0)
            fields.add(new MessageEmbed.Field("無", "", false));
        else
            scheduler.getQueue().forEach((track) -> {
                long songLength = track.getInfo().length / 1000;
                fields.add(new MessageEmbed.Field(track.getInfo().title, track.getInfo().isStream ? "**[LIVE]**" : "**[" + (timeCalculator(songLength)) + "]**", false));
            });

        return new MessageEmbed[]{createEmbed("歌曲列表", "",
                "",
                fields,
                null,
                0x7fc89a),
                nowPlaying};
    }

    public ActionRow controlButtons(String senderID, boolean pauseStatus, int loopStatus) {
        return ActionRow.of(
                Button.of(ButtonStyle.SECONDARY, senderID + ":musicLoopChange:" + botID, "",
                        loopStatus == 0 ? Emoji.fromUnicode("➡️") : (loopStatus == 1 ? Emoji.fromUnicode("\uD83D\uDD01") : Emoji.fromUnicode("\uD83D\uDD02"))),
                Button.of(ButtonStyle.SECONDARY, senderID + ":musicPause:" + botID, "",
                        pauseStatus ? Emoji.fromUnicode("▶️") : Emoji.fromUnicode("⏸️")),
                Button.of(ButtonStyle.SECONDARY, senderID + ":nextToPlay:" + botID, "", Emoji.fromUnicode("⏭️")),
                Button.of(ButtonStyle.SECONDARY, senderID + ":musicVolumeDown:" + botID, "", Emoji.fromUnicode("\uD83D\uDD09")),
                Button.of(ButtonStyle.SECONDARY, senderID + ":musicVolumeUp:" + botID, "", Emoji.fromUnicode("\uD83D\uDD0A")));
    }

    /**
     * functions
     */

    private String timeCalculator(long songLength) {
        StringBuilder builder = new StringBuilder();

        int hr = (int) songLength / 3600;
        int min = (int) ((songLength / 60) % 60);
        int sec = (int) songLength % 60;

        if (hr > 0)
            builder.append(hr < 10 ? "0" + hr : hr).append(':');
        builder.append(min < 10 ? "0" + min : min).append(':');
        builder.append(sec < 10 ? "0" + sec : sec);

        return builder.toString();
    }

    private void connectVC(Guild guild, VoiceChannel vc) {
        if (!guild.getAudioManager().isConnected()) {
            guild.getAudioManager().openAudioConnection(vc);
            // 新增bot到頻道
            musicBotManager.setBotToChannel(guild.getId(), vc.getId(), this);
        }
    }

    public GuildMusicManager getMusicManager(String guildID) {
        Guild guild = jda.getGuildById(guildID);
        if (guild == null)
            return null;
        return getMusicManager(guild);
    }

    private GuildMusicManager getMusicManager(Guild guild) {
        GuildMusicManager musicManager = musicManagers.get(guild.getId());

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager, guild);
            musicManager.scheduler.setManagerEvent(event);
            musicManagers.put(guild.getId(), musicManager);
        }

        return musicManager;
    }

    public void setActivity(String[] msg) {
        if (msg[0].equals("STREAMING")) {
            // name, url
            jda.getPresence().setActivity(Activity.of(Activity.ActivityType.STREAMING, msg[1], msg[2]));
        } else {
            Activity.ActivityType type = Activity.ActivityType.valueOf(msg[0]);
            jda.getPresence().setActivity(Activity.of(type, msg[1]));
        }
    }

    public String getID() {
        return botID;
    }
}
