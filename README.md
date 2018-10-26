# ExoPlayer DRMtoday integration

This repository hosts the DRMtoday ExoPlayer integration library. The library provides
an implementation of the `MediaDrmCallback` that can be used to send DRM license requests for
Widevine and PlayReady to DRMtoday.

You can create an instance of the callback as follows:

```
DrmtodayCallback drmtodayCallback = new DrmtodayCallback(
        httpDataSourceFactory, // The data source
        DrmtodayCallback.DRMTODAY_STAGING, // The Environment
        "six", // THe merchant
        "purchase", // The User ID
        "default", // The Session ID
        null, // The (optional) auth token
        "prestoplay", // The asset ID
        null // The variant ID
);
```

This will provide a statically configured instance that you can pass to your ExoPlayers
DrmSessionManager:

```
player = ExoPlayerFactory.newSimpleInstance(this,
        new DefaultRenderersFactory(this),
        new DefaultTrackSelector(),
        new DefaultDrmSessionManager<>(
                drmSystemUuid,
                mediaDrm,
                drmtodayCallback,
                null));
```

If you need to re-use the player instance for different content, you can also dynamically
configure the callback:

```
// Create a callback instance
DrmtodayCallback drmtodayCallback = new DrmtodayCallback(
        httpDataSourceFactory // The data source
);

// Since the DRM configuration is content specific, we need to
// configure the DRMtoday callback accordingly before we load content into the player
drmtodayCallback.configure(
        DrmtodayCallback.DRMTODAY_STAGING, // The Environment
        "six",       // The merchant
        "purchase",    // The User ID
        "default",   // The Session ID
        null,       // The (optional) auth token
        "prestoplay", // The asset ID
        null // The variant ID
);
```


## ExoPlayer versions

While the demo application in this repository is using ExoPlayer version 2.9.x, the
library itself is compatible with previous 2.x version of ExoPlayer.


## License

The library is released under the [Apache 2.0] (http://www.apache.org/licenses/LICENSE-2.0) license.