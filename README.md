capsulecrm-ciscoipphonedir
==========================

Link between Cisco XML directory and Capsule CRM for phone caller ID lookup.

### Configuration

In `conf/application.conf`, add your Capsule CRM URL and Capsule CRM API token.
Capsule CRM users can find their API token by visiting `My Preferences` via their username menu in the Capsule navigation bar.

```ruby
capsulecrm.url="https://<yourdomain>.capsulecrm.com"
capsulecrm.token="<your token here>"
```

### Running

```scala
sbt package
```

```
java -jar capsule-cisco.jar
```