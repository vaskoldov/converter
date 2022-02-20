<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"  xmlns:ns2="urn://x-artifacts-fssp-ru/mvv/smev3/application-documents/1.1.1" xmlns:c="urn://x-artifacts-fssp-ru/mvv/smev3/container/1.1.0"  xmlns:att="urn://x-artifacts-fssp-ru/mvv/smev3/attachments/1.1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fssp="http://www.fssprus.ru/namespace/IRequestOther/2020/1" xmlns:fssp3="http://www.fssprus.ru/namespace/IRequestOther/2021/1" xmlns:fssp2="http://www.fssprus.ru/namespace/incoming/2019/1">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>
	<xsl:param name="fileName"/>
	<xsl:param name="clientID"/>
	<xsl:param name="requestDate"/>
	<xsl:template match="/fssp:IRequestOther">
		<ns2:ApplicationDocumentsRequest>
			<c:ID>
				<xsl:value-of select="$clientID"/>
			</c:ID>
			<c:Date>
				<xsl:value-of select="$requestDate"/>
			</c:Date>
			<c:SenderID>FSOR01_3T</c:SenderID>
			<c:ReceiverID>FSSP01</c:ReceiverID>
			<c:ReceiverDepartmentCode>МВВ</c:ReceiverDepartmentCode>
			<c:Document>
				<c:ID>
					<xsl:value-of select="fssp:ExternalKey"/>
				</c:ID>
				<c:Type>
					<xsl:value-of select="fssp:DocType"/>
				</c:Type>
				<c:DocumentDate>
					<xsl:value-of select="fssp:DocDate"/>
				</c:DocumentDate>
				<c:DocumentNumber>БН</c:DocumentNumber>
				<c:AttachmentsBlock>
					<att:AttachmentDescription>
						<att:AttachmentFormat>
							<att:IsUnstructuredFormat>false</att:IsUnstructuredFormat>
							<att:IsZippedPacket>true</att:IsZippedPacket>
							<att:StructuredFormatType>http://www.fssprus.ru/namespace/IRequestOther/2020/1</att:StructuredFormatType>
						</att:AttachmentFormat>
						<att:AttachmentFilename>
							<xsl:value-of select="$fileName"/>
						</att:AttachmentFilename>
					</att:AttachmentDescription>
				</c:AttachmentsBlock>
			</c:Document>
		</ns2:ApplicationDocumentsRequest>
	</xsl:template>
	<xsl:template match="/fssp2:IRequest">
		<ns2:ApplicationDocumentsRequest>
			<c:ID>
				<xsl:value-of select="$clientID"/>
			</c:ID>
			<c:Date>
				<xsl:value-of select="$requestDate"/>
			</c:Date>
			<c:SenderID>FSOR01_3T</c:SenderID>
			<c:ReceiverID>FSSP01</c:ReceiverID>
			<c:ReceiverDepartmentCode>МВВ</c:ReceiverDepartmentCode>
			<c:Document>
				<c:ID>
					<xsl:value-of select="fssp2:ExternalKey"/>
				</c:ID>
				<c:Type>
					<xsl:value-of select="fssp2:DocType"/>
				</c:Type>
				<c:DocumentDate>
					<xsl:value-of select="fssp2:DocDate"/>
				</c:DocumentDate>
				<c:DocumentNumber>БН</c:DocumentNumber>
				<c:AttachmentsBlock>
					<att:AttachmentDescription>
						<att:AttachmentFormat>
							<att:IsUnstructuredFormat>false</att:IsUnstructuredFormat>
							<att:IsZippedPacket>true</att:IsZippedPacket>
							<att:StructuredFormatType>http://www.fssprus.ru/namespace/incoming/2019/1</att:StructuredFormatType>
						</att:AttachmentFormat>
						<att:AttachmentFilename>
							<xsl:value-of select="$fileName"/>
						</att:AttachmentFilename>
					</att:AttachmentDescription>
				</c:AttachmentsBlock>
			</c:Document>
		</ns2:ApplicationDocumentsRequest>
	</xsl:template>
	<xsl:template match="/fssp3:IRequestOther">
		<ns2:ApplicationDocumentsRequest>
			<c:ID>
				<xsl:value-of select="$clientID"/>
			</c:ID>
			<c:Date>
				<xsl:value-of select="$requestDate"/>
			</c:Date>
			<c:SenderID>FSOR01_3T</c:SenderID>
			<c:ReceiverID>FSSP01</c:ReceiverID>
			<c:ReceiverDepartmentCode>МВВ</c:ReceiverDepartmentCode>
			<c:Document>
				<c:ID>
					<xsl:value-of select="fssp3:ExternalKey"/>
				</c:ID>
				<c:Type>
					<xsl:value-of select="fssp3:DocType"/>
				</c:Type>
				<c:DocumentDate>
					<xsl:value-of select="fssp3:DocDate"/>
				</c:DocumentDate>
				<c:DocumentNumber>БН</c:DocumentNumber>
				<c:AttachmentsBlock>
					<att:AttachmentDescription>
						<att:AttachmentFormat>
							<att:IsUnstructuredFormat>false</att:IsUnstructuredFormat>
							<att:IsZippedPacket>true</att:IsZippedPacket>
							<att:StructuredFormatType>http://www.fssprus.ru/namespace/IRequestOther/2021/1</att:StructuredFormatType>
						</att:AttachmentFormat>
						<att:AttachmentFilename>
							<xsl:value-of select="$fileName"/>
						</att:AttachmentFilename>
					</att:AttachmentDescription>
				</c:AttachmentsBlock>
			</c:Document>
		</ns2:ApplicationDocumentsRequest>
	</xsl:template>	
</xsl:stylesheet>
