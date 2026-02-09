const axios = require("axios");


function get(url, headers) {
    return axios.get(url, {headers})
        .then(res => {
            return res;
        })
        .catch(error => {
            return error.response;
        });
}

function post(url, body, headers) {
    return axios.post(url, body, {headers})
        .then(res => {
            return res;
        })
        .catch(error => {
            return error.response;
        });
}

function put(url, body, headers) {
    return axios.put(url, body, {headers})
        .then(res => {
            return res;
        })
        .catch(error => {
            return error.response;
        });
}

function del(url, headers) {
    return axios.delete(url, {headers})
        .then(res => {
            return res;
        })
        .catch(error => {
            return error.response;
        });
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function createEvent(id, client_id = "IO", user_type = "G") {
    let json_event = {
        "id": id,
        "version": "1",
        "idPaymentManager": "11999923",
        "complete": "false",
        "missingInfo": [
            "paymentInfo.primaryCiIncurredFee",
            "paymentInfo.idBundle",
            "paymentInfo.idCiBundle"
        ],
        "debtorPosition": {
            "modelType": "2",
            "noticeNumber": "310978194271631307",
            "iuv": "10978194271631307"
        },
        "creditor": {
            "idPA": "66660006666",
            "idBrokerPA": "66660006666",
            "idStation": "66666666666_08",
            "companyName": "PA giacomo"
        },
        "psp": {
            "idPsp": "60001110001",
            "idBrokerPsp": "60001110001",
            "idChannel": "60000000001_08",
            "psp": "PSP Giacomo"
        },
        "debtor": {
            "fullName": "Debtor name integration test biz",
            "entityUniqueIdentifierType": "G",
            "entityUniqueIdentifierValue": "JHNDOE00A01B157N"
        },
        "payer": {
            "fullName": "Payer name integration test biz",
            "entityUniqueIdentifierType": "G",
            "entityUniqueIdentifierValue": "JHNDOE00A01B157C"
        },
        "paymentInfo": {
            "paymentDateTime": getCurrentDateTime5Digits(),
            "applicationDate": "2021-12-12",
            "transferDate": "2021-12-11",
            "dueDate": "2021-12-12",
            "paymentToken": "16cb4c797fd14d09899bdc161ff38d17",
            "amount": "25.0",
            "fee": "0.40",
            "totalNotice": "1",
            "paymentMethod": "creditCard",
            "touchpoint": "app",
            "remittanceInformation": "test",
            "IUR": "iur1234567890"
        },
        "transferList": [
            {
                "fiscalCodePA": "66660006666",
                "companyName": "PA giacomo",
                "amount": "2.00",
                "transferCategory": "paGetPaymentTest",
                "remittanceInformation": "/RFB/00202200000217527/5.00/TXT/"
            },
            {
                "fiscalCodePA": "66666666666",
                "companyName": "PA paolo",
                "amount": "8.00",
                "transferCategory": "paGetPaymentTest",
                "remittanceInformation": "/RFB/00202200000217527/5.00/TXT/"
            }
        ],
            "transactionDetails": {
              "origin": "PaymentManager",
              "transaction": {
                "idTransaction": "",
                "transactionId": "",
                "grandTotal": 2540,
                "amount": 2500,
                "fee": 40,
                "transactionStatus": "Confermato",
                "accountingStatus": "Contabilizzato",
                "rrn": "223560110624",
                "authorizationCode": "00",
                "creationDate": getCurrentDateTime9DigitsZ(), // prima
                "accountCode": "0037r972892475982475842",
                "psp": {
                  "idChannel": "05963231005_01_ONUS",
                  "businessName": "Nexi",
                  "serviceName": "Pagamento con Carte"
                },
                "origin": client_id
              },
              "wallet": {
                "idWallet": 125714007,
                "walletType": "Card",
                "enableableFunctions": ["pagoPA", "BPD", "FA"],
                "pagoPa": true,
                "onboardingChannel": "IO",
                "favourite": false,
                "createDate": "2022-12-22T13:07:25Z",
                "info": {
                  "type": "CP",
                  "holder": "Payer name integration test biz",
                  "blurredNumber": "0403",
                  "hashPan": "e88aadfd9f40e1482615fd3c8c44f05c53f93aed1bcea69e82b3e5e27668f822",
                  "expireMonth": "06",
                  "expireYear": "2026",
                  "brand": "MASTERCARD",
                  "brandLogo": "https://wisp2.pagopa.gov.it/wallet/assets/img/creditcard/carta_visa.png"
                }
              },
              "user": {
                "fiscalCode": "JHNDOE00A01B157C",
                "userId": "677676786",
                "userStatus": "11",
                "userStatusDescription": "REGISTERED_SPID",
                "name": "Payer name",
                "surname": "integration test biz",
                "type": user_type
              },
              "info": {
                "brand": "MASTERCARD",
                "brandLogo": "https://checkout.pagopa.it/assets/creditcard/mastercard.png",
                "clientId": client_id,
                "paymentMethodName": "CARDS",
                "type": "CP"
              }
            }
    }
    return json_event
}

// Utility per padding con zeri a sinistra
function padLeft(num, size) {
  return String(num).padStart(size, '0');
}

/**
 * Formato locale:
 * "YYYY-MM-DDTHH:mm:ss.SSSSS"
 * Esempio: "2025-11-02T11:14:57.16758"
 */
function getCurrentDateTime5Digits() {
  const now = new Date();

  const year = now.getFullYear();
  const month = padLeft(now.getMonth() + 1, 2);
  const day = padLeft(now.getDate(), 2);
  const hour = padLeft(now.getHours(), 2);
  const minute = padLeft(now.getMinutes(), 2);
  const second = padLeft(now.getSeconds(), 2);

  const millis = padLeft(now.getMilliseconds(), 3); // "SSS"

  // 2 cifre extra semplici: riuso delle prime 2 cifre dei ms
  const extra = millis.slice(0, 2); // es. "167" -> "16"

  const fraction5 = millis + extra; // "SSSSS"

  return `${year}-${month}-${day}T${hour}:${minute}:${second}.${fraction5}`;
}

/**
 * Formato UTC:
 * "YYYY-MM-DDTHH:mm:ss.SSSSSSSSSZ"
 * Esempio: "2025-11-02T10:14:57.218496702Z"
 */
function getCurrentDateTime9DigitsZ() {
  const now = new Date();

  const year = now.getUTCFullYear();
  const month = padLeft(now.getUTCMonth() + 1, 2);
  const day = padLeft(now.getUTCDate(), 2);
  const hour = padLeft(now.getUTCHours(), 2);
  const minute = padLeft(now.getUTCMinutes(), 2);
  const second = padLeft(now.getUTCSeconds(), 2);

  const millis = padLeft(now.getUTCMilliseconds(), 3); // "SSS"

  // Ripetiamo i ms finché non arriviamo a 9 cifre
  // es. "218" -> "218218218" (9 cifre)
  const fraction9 = (millis + millis + millis).slice(0, 9);

  return `${year}-${month}-${day}T${hour}:${minute}:${second}.${fraction9}Z`;
}

module.exports = {
    get, post, put, del, createEvent, sleep
}
