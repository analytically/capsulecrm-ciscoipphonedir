capsulecrm-ciscoipphonedir [![Build Status](https://travis-ci.org/analytically/capsulecrm-ciscoipphonedir.svg?branch=master)](https://travis-ci.org/analytically/capsulecrm-ciscoipphonedir)
==========================

Use [Capsule CRM](http://capsulecrm.com/) on a [Cisco IP phone](http://www.cisco.com/cisco/web/solutions/small_business/products/voice_conferencing/SPA_500/index.html). Uses Capsule CRM [REST API](http://developer.capsulecrm.com/).

Development sponsored by [Coen Recruitment](http://www.coen.co.uk). Follow [@analytically](http://twitter.com/analytically) for updates.

![screenshot1](images/screenshot1.jpg)
![screenshot2](images/screenshot2.jpg)
![screenshot3](images/screenshot3.jpg)
![screenshot4](images/screenshot4.jpg)

### Functionality

  - Search your contacts (persons and organisations) straight from your phone
  - Search your contacts by tag
  - Saves your last 10 searches (per phone)
  - Pagination through pressing the `next` soft key
  - Rate limiting (defaults to 3 requests/second per phone) to prevent abuse
  - Built using [spray](http://spray.io/), a [high-performance](http://spray.io/blog/2013-05-24-benchmarking-spray/)
    REST/HTTP toolkit, should easily support > 1000 connected phones

Planned:

  - Access the recently modified list

### Requirements

[Java 7](http://java.com/en/download/index.jsp) or later. A Capsule CRM account and token.

### Building (optional)

```
sbt assembly
```

This builds a single, executable 'fat' jar in `target/scala-2.10`.

### Running

Prebuilt releases are available [here](https://github.com/analytically/capsulecrm-ciscoipphonedir/releases). Capsule CRM users
can find their API token by visiting `My Preferences` via their username menu in the Capsule navigation bar.

```
java -Dlogback.configurationFile=logback.production.xml -Dhostname=capsulecisco.example.com -Dhttp.interface=0.0.0.0 -Dcapsulecrm.url=https://example.capsulecrm.com -Dcapsulecrm.token=abcdef123456789 -jar capsule-cisco.jar
```

Running with [authbind](http://mutelight.org/authbind) on Debian/Ubuntu:

```
authbind --deep java -Dlogback.configurationFile=logback.production.xml -Dhttp.interface=0.0.0.0 -Dhttp.port=80 -Dhostname=capsulecisco.example.com -Dcapsulecrm.url=https://example.capsulecrm.com -Dcapsulecrm.token=1234 -Djava.net.preferIPv4Stack -jar capsule-cisco.jar
```

##### HTTPS?

Unfortunately Cisco IP phones do not support XML services over HTTPS.

### Ubuntu Upstart

```
start on started network-interface INTERFACE=eth0
stop on stopping network-interface INTERFACE=eth0

env JVM_OPTIONS="-Xmx1024M -XX:+UseConcMarkSweepGC -XX:+AggressiveOpts -XX:MaxPermSize=300M -XX:+CMSClassUnloadingEnabled -XX:SurvivorRatio=8 -XX:+ExplicitGCInvokesConcurrent"
env APP_OPTIONS="-Dlogback.configurationFile=logback.production.xml -Dhostname=capsulecisco.example.com -Dhttp.interface=0.0.0.0 -Dhttp.port=8082 -Dcapsulecrm.url=https://example.capsulecrm.com -Dcapsulecrm.token=abcdef123456789"

chdir /usr/share/capsule-cisco

script
    exec java $JVM_OPTIONS $APP_OPTIONS -jar capsule-cisco.jar 2>&1
end script
```

### Cisco IP Phone Setup

  - Go to your phone's `Configuration Utility`
  - Click `Admin login` and `advanced`
  - Go to the `Phone` tab
  - Under `XML Service`, specify where you are running this script (e.g. `http://192.168.0.6/capsule.xml`) and click `Submit All Changes`:

![ciscoweb](images/ciscoweb.png)

### Tested on

  - [Cisco SPA504G](http://www.cisco.com/en/US/prod/collateral/voicesw/ps6788/phones/ps10499/data_sheet_c78-548564.html)

#### Dial Plans

By default, the Cisco SPA504G comes with the following dial plan:

```
(*xx|[3469]11|0|00|[2-9]xxxxxx|1xxx[2-9]xxxxxxS0|xxxxxxxxxxxx.)
```

Since we're based in the UK, we're using:

```
(*xx|<0:0044>[12357]xxxxxxxx.|<0:0044>[58][0][0]xxxxx.|00xxxxxxxxx.)
```

  - *xx = allow star type services
  - <0:0044>[123]xxxxxxxx. = convert all 01/02 or 03 calls into international format i.e. 00441 or 00442
  - <0:0044>[58][0][0]xxxxx. = allow 0800 and 0500 calls (freefone calls) and block anything else e.g. 0870
  - 00xxxxxxxxx. = allow international numbers starting 00 with at least 9 digits following

### License

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Copyright 2013-2015 [Mathias Bogaert](mailto:mathias.bogaert@gmail.com).
