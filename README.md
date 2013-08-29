capsulecrm-ciscoipphonedir [![Build Status](https://travis-ci.org/coenrecruitment/capsulecrm-ciscoipphonedir.png)](https://travis-ci.org/coenrecruitment/capsulecrm-ciscoipphonedir)
==========================

Search [Capsule CRM](http://capsulecrm.com/) from your [Cisco IP phone](http://www.cisco.com/cisco/web/solutions/small_business/products/voice_conferencing/SPA_500/index.html). Uses Capsule CRM [REST API](http://developer.capsulecrm.com/).

Development sponsored by [Coen Recruitment](http://www.coen.co.uk). Follow [@analytically](http://twitter.com/analytically) for updates.

### Requirements

Java 6 or later. A Capsule CRM account and token.

### Configuration

In `conf/application.conf`, add your server hostname or IP address, Capsule CRM URL and Capsule CRM API token.
Capsule CRM users can find their API token by visiting `My Preferences` via their username menu in the Capsule navigation bar.

```ruby
hostname=capsulecisco.coen.co.uk
capsulecrm.url="https://<yourdomain>.capsulecrm.com"
capsulecrm.token="<your token here>"
```

### Building

```
sbt assembly
```

### Running

```
java -jar target/scala-2.10/capsule-cisco.jar
```

### Cisco IP Phone Setup

Todo

### Validated On

- Cisco SPA504G

### License

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Copyright 2013 Coen Recruitment Ltd - www.coen.co.uk.
