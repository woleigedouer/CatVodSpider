package com.github.catvod.spider;

public class EpisodeInfo {
    private String episodeNum;
    private String episodeName;
    private String episodeYear;
    private String episodeSeasonNum;
    private String seriesName;
    private String fileName;
    private String episodeUrl;

    public String getEpisodeNum() {
        return episodeNum;
    }

    public void setEpisodeNum(String episodeNum) {
        this.episodeNum = episodeNum;
    }

    public String getEpisodeName() {
        return episodeName;
    }

    public void setEpisodeName(String episodeName) {
        this.episodeName = episodeName;
    }

    public String getEpisodeYear() {
        return episodeYear;
    }

    public void setEpisodeYear(String episodeYear) {
        this.episodeYear = episodeYear;
    }

    public String getEpisodeSeasonNum() {
        return episodeSeasonNum;
    }

    public void setEpisodeSeasonNum(String episodeSeasonNum) {
        this.episodeSeasonNum = episodeSeasonNum;
    }

    public String getSeriesName() {
        return seriesName;
    }

    public void setSeriesName(String seriesName) {
        this.seriesName = seriesName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getEpisodeUrl() {
        return episodeUrl;
    }

    public void setEpisodeUrl(String episodeUrl) {
        this.episodeUrl = episodeUrl;
    }

    @Override
    public String toString() {
        return "EpisodeInfo{" +
                "episodeNum='" + episodeNum + '\'' +
                ", episodeName='" + episodeName + '\'' +
                ", episodeYear='" + episodeYear + '\'' +
                ", episodeSeasonNum='" + episodeSeasonNum + '\'' +
                ", seriesName='" + seriesName + '\'' +
                ", fileName='" + fileName + '\'' +
                ", episodeUrl='" + episodeUrl + '\'' +
                '}';
    }
}
