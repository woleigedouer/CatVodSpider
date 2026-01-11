package com.github.catvod.spider;

import android.app.Activity;
import com.github.catvod.bean.danmu.DanmakuItem;
import com.google.gson.Gson;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class WebServer extends NanoHTTPD {

    public WebServer(int port) throws IOException {
        super(port);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri.equals("/")) {
            return newFixedLengthResponse(getHtml());
        } else if (uri.equals("/search")) {
            Map<String, String> params = session.getParms();
            String keyword = params.get("keyword");
            Activity activity = Utils.getTopActivity();
            List<DanmakuItem> results = LeoDanmakuService.manualSearch(keyword, activity);
            return newFixedLengthResponse(new Gson().toJson(results));
        } else if (uri.equals("/select")) {
            Map<String, String> params = session.getParms();
            String danmakuUrl = params.get("url");
            for (DanmakuItem item : DanmakuManager.lastDanmakuItemMap.values()) {
                if (item.getDanmakuUrl().equals(danmakuUrl)) {
                    Activity activity = Utils.getTopActivity();
                    LeoDanmakuService.pushDanmakuDirect(item, activity, false);
                    return newFixedLengthResponse("OK");
                }
            }
            return newFixedLengthResponse("Danmaku not found");
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
    }

    private String getHtml() {
        return "<!DOCTYPE html><html><head><title>Danmaku Control</title><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><style>" +
                "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f0f2f5; margin: 0; padding: 20px; }" +
                "h1 { color: #333; text-align: center; } " +
                ".container { max-width: 600px; margin: 0 auto; background-color: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); } " +
                ".search-box { display: flex; margin-bottom: 20px; } " +
                "#keyword { flex-grow: 1; border: 1px solid #ccc; border-radius: 4px; padding: 10px; font-size: 16px; } " +
                "button { background-color: #007bff; color: white; border: none; padding: 10px 15px; border-radius: 4px; cursor: pointer; font-size: 16px; margin-left: 10px; } " +
                "button:hover { background-color: #0056b3; } " +
                "#results { margin-top: 20px; } " +
                ".result-item { background-color: #f8f9fa; padding: 15px; border: 1px solid #dee2e6; border-radius: 4px; margin-bottom: 10px; cursor: pointer; } " +
                ".result-item:hover { background-color: #e9ecef; } " +
                "</style></head><body>" +
                "<div class='container'>" +
                "<h1>Danmaku Control</h1>" +
                "<div class='search-box'>" +
                "<input type='text' id='keyword' placeholder='Enter keyword'>" +
                "<button onclick='search()'>Search</button>" +
                "</div>" +
                "<div id='results'></div>" +
                "</div>" +
                "<script>" +
                "function search() {" +
                "  var keyword = document.getElementById('keyword').value;" +
                "  fetch('/search?keyword=' + encodeURIComponent(keyword))" +
                "    .then(response => response.json())" +
                "    .then(data => {" +
                "      var resultsDiv = document.getElementById('results');" +
                "      resultsDiv.innerHTML = '';" +
                "      data.forEach(item => {" +
                "        var div = document.createElement('div');" +
                "        div.className = 'result-item';" +
                "        div.innerText = item.title + ' - ' + item.epTitle;" +
                "        div.onclick = function() { select(item.danmakuUrl); };" +
                "        resultsDiv.appendChild(div);" +
                "      });" +
                "    });" +
                "}" +
                "function select(url) {" +
                "  fetch('/select?url=' + encodeURIComponent(url));" +
                "}" +
                "</script>" +
                "</body></html>";
    }
}
