# Claiming names

## Namespace address
* based on sever bitcoin address
* since we know the recipient address, we can generate p2sh for a name and query
  - generate query bloomfilter based on p2sh including namespace address

## Fee registration transactions
* namespace address makes payment to itself designating registration fee going forward
  - can change over time with new frts
  - server pulls its own fee from blockchain

pay to script hash
