<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fssp="urn://x-artifacts-fssp-ru/mvv/smev3/application-documents/1.1.1" xmlns:c="urn://x-artifacts-fssp-ru/mvv/smev3/container/1.1.0">
	<xsl:output method="xml" encoding="UTF-8"/>
	<xsl:param name="ClientID"/>
	<xsl:param name="ToClientID"/>
	<xsl:template match="/">
		<tns:ClientMessage xmlns:tns="urn://x-artefacts-smev-gov-ru/services/service-adapter/types">
			<tns:itSystem>FSOR01_3T</tns:itSystem>
			<tns:ResponseMessage>
				<tns:ResponseMetadata>
					<tns:clientId>
						<xsl:value-of select="$ClientID"/>
					</tns:clientId>
					<tns:replyToClientId>
						<xsl:value-of select="$ToClientID"/>
					</tns:replyToClientId>
				</tns:ResponseMetadata>
				<tns:ResponseContent>
					<tns:content>
						<tns:MessagePrimaryContent>
							<xsl:apply-templates select="//fssp:ApplicationDocumentsRequest"/>
						</tns:MessagePrimaryContent>
					</tns:content>
				</tns:ResponseContent>
			</tns:ResponseMessage>
		</tns:ClientMessage>
	</xsl:template>
	<xsl:template match="//fssp:ApplicationDocumentsRequest">
		<fssp:ApplicationDocumentsResponse xmlns:fssp="urn://x-artifacts-fssp-ru/mvv/smev3/application-documents/1.1.1" xmlns:c="urn://x-artifacts-fssp-ru/mvv/smev3/container/1.1.0">
			<c:ID>038e7a1f-6c51-4843-8155-acf3e169afb1</c:ID>
			<c:ReferencedPackId>12f1a3cd-cd5f-48fe-8eb4-f73e3d9f8a8g</c:ReferencedPackId>
			<c:Date>2015-10-16T12:00:00</c:Date>
			<c:ReceiptResult>SUCCESS</c:ReceiptResult>
			<c:DocumentReceipt>
				<c:ID>28251007270018</c:ID>
				<c:ReferencedDocumentId>1256</c:ReferencedDocumentId>
				<c:ReceiptDate>2015-10-16T12:00:00</c:ReceiptDate>
				<c:ReceiptId>93</c:ReceiptId>
			</c:DocumentReceipt>
		</fssp:ApplicationDocumentsResponse>
	</xsl:template>
</xsl:stylesheet>
