package com.luna.aggarly.vision.search.records;

import java.util.List;

public record ThreeChannelResults(
        List<ChannelSearchResult> imageChannel,
        List<ChannelSearchResult> captionChannel,
        List<ChannelSearchResult> descriptionChannel
) {}
