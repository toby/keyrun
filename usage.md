# key.run

## Usage

```markdown
$ keyrun --local
Enter the Bitcoin address for this server: 1JeyWVgTUtm3zuhBRSqnPXQjGoRe4xUbpT
Checking if registered... Not registered!
To register this server send 0.001 uBTC to: 14jFdgcBrhDdrk8NteM28KPgsnmHuV91iH
Waiting for registration... Registered!
```

## Login address

* bitcoin address you send a small amount to to login
* verified because your transaction is signed with matching blockchain name record

## Bitcoin voting

* all content gets a bitcoin address
  - deterministic address based on author's public key
* ui sorts content by funds and/or # of unique senders

## DHT filter/content downvoting

* downvotes are *not* Bitcoin transactions
* social or vote based filter on content stored distributed
* multiple filters available
