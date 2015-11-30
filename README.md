# key.run

## What is it?

Key.run is an open source, distributed content publication app built on
Bitcoin and BitTorrent. Content pointers known as magnet links are
stored on the Bitcoin blockchain and content is resolved on the
BitTorrent network.

Since key.run uses the blockchain as its db and BitTorrent for content
storage, it operates without a centralized server and is designed to be
highly resistant to censorship or manipulation.

## Get running

The fastest way to use key.run is to visit http://key.run and start
downloading content with any BitTorrent client that supports magnet
links. We recommend [Transmission](http://www.transmissionbt.com).

Publishing content on key.run requires paying a small Bitcoin fee using
the official [Bitcoin Core](https://bitcoin.org/en/download) client
(additional wallet support coming soon).

To publish a magnet link, paste the full link url into the text field at
the top of key.run and click **Run**. This will open up Bitcoin Core and
load the transaction needed to publish your link.

Once you submit your payment using your wallet, your link will show up
on key.run.

## Hosting your own key.run server

The http://key.run website is hosted as a reference instance of the
server but you are encouraged to download and host your own server.

Key.run is currently built in Clojure so you'll need the following
installed on your machine:

* [Java](https://www.java.com/en/download/)
* [Leiningen](http://leiningen.org)

You can then [download the key.run source](https://git.playgrub.com/toby/keyrun)
and run it with the command `lein run 1J7THZL4mJ7uhvUx9E5FVaNcconWY1BnQS`.

You can also build the project as an uberjar and run it on any machine
that has Java installed (Leiningen isn't needed).

Once started, the key.run server will synchronize with the Bitcoin
network then be available for use. By default the web server runs on
port 9090.

## Namespace keys

A namespace key is a Bitcoin address that is monitored for key.run
transactions. Messages published to one namespace will not be visible in
other namespaces.

When you start the key.run server you have to give it a namespace key.
The default key.run namespace is **1J7THZL4mJ7uhvUx9E5FVaNcconWY1BnQS**.
The server uses Bitcoin [SPV](https://bitcoin.org/en/developer-guide#simplified-payment-verification-spv)
and will only look for key.run transactions on that bitcoin address. All
key.run fees are sent to the namespace key.

## BIP-70 Payment Protocol

Key.run uses the [BIP-70](https://github.com/bitcoin/bips/blob/master/bip-0070.mediawiki)
Payment Protocol to construct proper key.run transactions which are then
passed to the Bitcoin Core client. This means each client publishes
content directly to the Bitcoin network and no private keys are stored
on the key.run server.

## OP_RETURN

Bitcoin transactions can store 40 bytes of data using [OP_RETURN](http://bitzuma.com/posts/op-return-and-the-future-of-bitcoin/).
BitTorrent magnet links contain a 40 byte hash of a torrent file. That
hash is stored on the blockchain in the OP_RETURN output of the
transaction constructed by key.run.

## Future plans

Key.run is a nascent technology and is meant as a technology
demonstration. Future versions will resolve magnet link metadata, allow
for sub namespace creation, sort and score content with Bitcoin based
voting and allow users to earn Bitcoin by splitting fees with content
publishers and sub namespace creators.

## Who did this?

Key.run is built by [Toby](http://twitter.com/toby). Feel free to hit
him up with questions.
