package it.gov.pagopa.bizeventsdatastore.entity;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import it.gov.pagopa.bizeventsdatastore.entity.enumeration.RefNumberType;
import it.gov.pagopa.bizeventsdatastore.entity.enumeration.StatusType;
import it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt.*;
import it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt.Transaction;
import it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BizEvent {
	private String id;
	private String version;
	private String idPaymentManager;
	private String complete;
	private String receiptId;
	private List<String> missingInfo;
	private DebtorPosition debtorPosition;
	private Creditor creditor;
	private Psp psp;
	private Debtor debtor;
	private Payer payer;
	private PaymentInfo paymentInfo;
	private List<Transfer> transferList;
	private TransactionDetails transactionDetails;
	private Long timestamp;  // to be valued with ZonedDateTime.now().toInstant().toEpochMilli();
	private Map<String, Object> properties;
	
	// internal management field
	@Builder.Default
	private StatusType eventStatus = StatusType.NA;
	@Builder.Default
	private Integer eventRetryEnrichmentCount = 0;
	@Builder.Default
	private Boolean eventTriggeredBySchedule = Boolean.FALSE;
	private String eventErrorMessage;

	//PDF Receipt
	private Transaction transaction;
	private User user;
	private Cart cart;

	public void alignPdfReceipt() {
		if(this.payer.getEntityUniqueIdentifierValue() == null) {
			return;
		}
		Payee payee = null;
		it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt.Debtor debtor = null;
		if(this.creditor != null && this.debtor != null){
			payee = Payee.builder()
					.name(this.creditor.getOfficeName())
					.taxCode(this.creditor.getCompanyName())
					.build();
			debtor = it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt.Debtor.builder()
					.fullName(this.debtor.getFullName())
					.taxCode(this.debtor.getEntityUniqueIdentifierValue())
					.build();
		}
		String value = null;
		RefNumberType type = null;
		if(this.debtorPosition != null) {
			if(this.debtorPosition.getModelType().equals("1")) {
				value = this.debtorPosition.getIuv();
				type = RefNumberType.CODICE_AVVISO;
			} else if(this.debtorPosition.getModelType().equals("2")) {
				value = this.debtorPosition.getIuv();
				type = RefNumberType.IUV;
			}
		}
		RefNumber refNumber = RefNumber.builder()
				.value(value)
				.type(type)
				.build();
		Item item = null;
		Cart cart = null;
		if(this.paymentInfo != null){
			item = Item.builder()
					.subject(this.paymentInfo.getRemittanceInformation())
					.amount(this.paymentInfo.getAmount())
					.refNumber(refNumber)
					.debtor(debtor)
					.payee(payee)
					.build();
			cart = Cart.builder()
					.amountPartial(this.paymentInfo.getAmount())
					.item(item)
					.build();
		}
		Data data = null;
		if(this.payer == null) {
			data = Data.builder()
					.fullName(this.payer.getFullName())
					.taxCode(this.payer.getEntityUniqueIdentifierValue())
					.build();
		}
		User user = User.builder()
				.data(data)
				.build();
		if(this.getTransactionDetails() != null && this.getTransactionDetails().getWallet() != null && this.getTransactionDetails().getTransaction() != null) {
			PaymentMethod paymentMethod = this.mapPaymentMethod();
			it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt.Psp psp = it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt.Psp.builder()
					.name(mapPspName())
					.fee((Objects.isNull(transactionDetails.getTransaction().getFee())) ? String.valueOf(transactionDetails.getTransaction().getFee()) : this.paymentInfo.getFee())
					.build();
			Transaction transaction = mapTransaction(psp, paymentMethod);
			this.transaction = transaction;
		}
		this.user = user;
		this.cart = cart;
	}

	private PaymentMethod mapPaymentMethod() {
		String brand = this.transactionDetails.getWallet().getInfo().getBrand();
		String holder = (transactionDetails.getWallet().getInfo().getHolder() != null) ? transactionDetails.getWallet().getInfo().getHolder() : this.payer.getFullName();
		return PaymentMethod.builder()
				.name(brand)
				.accountHolder(holder)
				.build();
	}

	private String mapPspName() {
		String name = null;
		if(this.transactionDetails.getTransaction().getPsp() != null && this.transactionDetails.getTransaction().getPsp().getBusinessName() != null) {
			name = this.transactionDetails.getTransaction().getPsp().getBusinessName();
		} else {
			name = (this.psp != null && this.psp.getPsp() != null) ? this.psp.getPsp() : null;
		}
		return name;
	}

	private Transaction mapTransaction(it.gov.pagopa.bizeventsdatastore.entity.pdfReceipt.Psp psp, PaymentMethod paymentMethod) {
		long id;
		if(Objects.isNull(this.transactionDetails.getTransaction().getIdTransaction())) {
			id = (this.paymentInfo.getPaymentToken() != null) ? Long.parseLong(this.paymentInfo.getPaymentToken()) : Long.parseLong(this.paymentInfo.getIUR());
		} else {
			id = this.transactionDetails.getTransaction().getIdTransaction();
		}
		String timestamp = (transactionDetails.getTransaction().getCreationDate() != null) ? transactionDetails.getTransaction().getCreationDate() : this.paymentInfo.getPaymentDateTime();
		String amount = (!Objects.isNull(transactionDetails.getTransaction().getAmount())) ? String.valueOf(transactionDetails.getTransaction().getAmount()) : this.paymentInfo.getAmount();
		boolean requestedByDebtor = this.payer == null || !this.payer.getEntityUniqueIdentifierValue().equals(this.debtor.getEntityUniqueIdentifierValue());
		String rrn = null;
		if(this.transactionDetails.getTransaction().getRrn() == null) {
			rrn = (this.paymentInfo.getPaymentToken() != null) ? this.paymentInfo.getPaymentToken() : this.paymentInfo.getIUR();
		} else {
			rrn = this.transactionDetails.getTransaction().getRrn();
		}
		return Transaction.builder()
				.id(id)
				.timestamp(timestamp)
				.amount(amount)
				.requestedByDebtor(requestedByDebtor)
				.rrn(rrn)
				.authCode(this.transactionDetails.getTransaction().getAuthorizationCode())
				.psp(psp)
				.paymentMethod(paymentMethod)
				.build();
	}
}
