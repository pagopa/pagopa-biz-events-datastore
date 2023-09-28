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
	console.log(error)
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

function createEvent(id) {
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
            "fullName": "paGetPaymentName",
            "entityUniqueIdentifierType": "G",
            "entityUniqueIdentifierValue": "44445554444"
        },
        "payer": {
            "fullName": "name",
            "entityUniqueIdentifierType": "G",
            "entityUniqueIdentifierValue": "77776667777_01"
        },
        "paymentInfo": {
            "paymentDateTime": "2022-10-24T15:09:16.987603",
            "applicationDate": "2021-12-12",
            "transferDate": "2021-12-11",
            "dueDate": "2021-12-12",
            "paymentToken": "16cb4c797fd14d09899bdc161ff38d17",
            "amount": "10.50",
            "fee": "2.00",
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
        ]
    }
    return json_event
}


module.exports = {
    get, post, put, del, createEvent, sleep
}
