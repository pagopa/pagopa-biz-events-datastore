

export function randomString(length, charset) {
    let res = '';
    while (length--) res += charset[(Math.random() * charset.length) | 0];
    return res;
}


export function getPaymentEventForTest(id) {
    const idPA = randomString(11, "0123456789");
    const idPSP = randomString(11, "0123456789");
    return {
        "id": id,
        "version": "1",
        "idPaymentManager": randomString(8, "0123456789"),
        "complete": "false",
        "missingInfo": [
            "paymentInfo.primaryCiIncurredFee",
            "paymentInfo.idBundle",
            "paymentInfo.idCiBundle"
        ],
        "debtorPosition": {
            "modelType": "2",
            "noticeNumber": randomString(18, "0123456789"),
            "iuv": randomString(17, "0123456789")
        },
        "creditor": {
            "idPA": idPA,
            "idBrokerPA": idPA,
            "idStation": `${idPA}_08`,
            "companyName": "PA test_company"
        },
        "psp": {
            "idPsp": idPSP,
            "idBrokerPsp": idPSP,
            "idChannel": `${idPSP}_08`,
            "psp": "test_PSP"
        },
        "debtor": {
            "fullName": "paTestName",
            "entityUniqueIdentifierType": "G",
            "entityUniqueIdentifierValue": randomString(11, "0123456789")
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
            "remittanceInformation": "test"
        },
        "transferList": [
            {
                "fiscalCodePA": idPA,
                "companyName": "PA test_company",
                "amount": "2.00",
                "transferCategory": "paTest",
                "remittanceInformation": "/RFB/00202200000217527/5.00/TXT/"
            },
            {
                "fiscalCodePA": randomString(11, "0123456789"),
                "companyName": "PA test_company",
                "amount": "8.00",
                "transferCategory": "paTest",
                "remittanceInformation": "/RFB/00202200000217527/5.00/TXT/"
            }
        ]
    }
}
