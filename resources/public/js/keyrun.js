// example magnet links
// magnet:?xt=urn:btih:43A5C093C341745501A740F96E77A93D6E3D7727&dn=talvar+2015+720p+bluray+x264+hindi+5+1ch+mrdhila&tr=udp%3A%2F%2Ftracker.publicbt.com%2Fannounce&tr=udp%3A%2F%2Fglotorrents.pw%3A6969%2Fannounce
// magnet:?xt=urn:btih:52734D60E1B6AFE4567CCAD8A3D8A5C3E4F81670&dn=halo+the+fall+of+reach+2015+multi+1080p+bluray+x264+melba+mkv&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80%2Fannounce&tr=udp%3A%2F%2Fglotorrents.pw%3A6969%2Fannounce

var keyrun = {};

keyrun.getToEncode = function() {
  return document.getElementById("encode-value").value
};

keyrun.createPaymentURL = function(string) {
  if (string) {
    return "bitcoin:?r=http%3A%2F%2F" + encodeURIComponent(location.host) + "%2Fkr%2Fmessage%2Fpayreq%3Fmessage%3D" + encodeURIComponent(string.replace(/ /g, "+"))
  }
};

keyrun.payIt = function() {
  var encodeValue = keyrun.getToEncode();
  if (encodeValue && encodeValue.startsWith("magnet:?")) {
    var paymentURL = keyrun.createPaymentURL(encodeValue.replace(/^.*btih:([^&]*).*$/,'$1'));
    console.log("PaymentURL: " + paymentURL);
    window.open(paymentURL);
    document.getElementById("encode-value").value = "";
  } else {
    alert("Please enter a valid magnet URL")
  }
  return false;
};
