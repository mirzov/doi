package se.lu.nateko.cp.doi.meta

import scala.collection.Seq
import scala.collection.immutable
import scala.util.Success
import scala.util.Try
import scala.util.Failure
import java.net.URI
import scala.util.matching.Regex

trait SelfValidating{
	def error: Option[String]

	protected def joinErrors(errors: Iterable[Option[String]]): Option[String] = {
		val list = errors.flatten
		if(list.isEmpty) None else Some(list.mkString("\n"))
	}
	protected def joinErrors(errors: Option[String]*): Option[String] = joinErrors(errors)

	protected def allGood(items: Iterable[SelfValidating]): Option[String] = joinErrors(items.map(_.error))

	protected def nonNull(obj: AnyRef)(msg: String): Option[String] =
		if(obj == null) Some(msg) else None

	protected def nonEmpty[T](seq: Iterable[T])(msg: String): Option[String] =
		if(seq == null || seq.isEmpty) Some(msg) else None

	protected def eachNonEmpty(ss: Seq[String])(msg: String): Option[String] =
		joinErrors(ss.map(s => nonEmpty(s)(msg)))

	protected def nonEmptyAllGood(items: Iterable[SelfValidating])(msg: String): Option[String] =
		if(items.isEmpty) Some(msg) else allGood(items)

	//TODO Improve this naive URI syntax validation
	protected def validUri(uri: String): Option[String] =
		if(SelfValidating.uriRegex.findFirstIn(uri).isDefined) None else Some("Invalid URI: " + uri)

}

object SelfValidating{
	private val uriRegex = """^https?://.+$""".r
}

sealed trait Name extends SelfValidating

case class PersonalName(givenName: String, familyName: String) extends Name{
	def error = joinErrors(
		nonEmpty(givenName)("Given name is required"),
		nonEmpty(familyName)("Family name is required")
	)
	override def toString = givenName + " " + familyName
}

case class GenericName(name: String) extends Name{
	def error = nonEmpty(name)("Name is required")
	override def toString = name
}

case class NameIdentifier(nameIdentifier: String, scheme: NameIdentifierScheme) extends SelfValidating{
	import NameIdentifierScheme._

	def error = joinErrors(
		nonEmpty(nameIdentifier)("Name identifier must not be empty"),
		nonNull(scheme)("Name Identifier scheme must be provided"),
		scheme.error,
		lookupRegex(scheme.nameIdentifierScheme) match{
			case Some(rex) =>
				if(rex.matches(nameIdentifier)) None
				else Some(s"Wrong ${scheme.nameIdentifierScheme} ID format")
			case None if(supported.contains(scheme)) => None
			case None =>
				val supportedNames = supported.mkString(", ")
				Some("Only the following name identifier schemes are supported: " + supportedNames)
		}
	)
}

object NameIdentifier{
	def orcid(id: String) = NameIdentifier(id, NameIdentifierScheme.Orcid)
	def isni(id: String) = NameIdentifier(id, NameIdentifierScheme.Isni)
}

case class NameIdentifierScheme(nameIdentifierScheme: String, schemeUri: Option[String]) extends SelfValidating{
	def error = joinErrors(
		nonEmpty(nameIdentifierScheme)("Name identifier scheme must have a name"),
		schemeUri.flatMap(validUri)
	)
	override def toString = nameIdentifierScheme
}

case class FunderIdentifier(funderIdentifier: Option[String], scheme: Option[FunderIdentifierScheme]) extends SelfValidating {
	import FunderIdentifierScheme._

	def error = joinErrors(
		funderIdentifier.collect{
			case fi if !fi.isEmpty && scheme.isEmpty => "Funder Identifier scheme must be provided"
		},
		scheme.flatMap{scheme =>
			val validator = lookupValidator(scheme.funderIdentifierType)

			validator.fold{Some("Only the following funder identifier schemes are supported: " + supported.mkString(", "))}{fIdVal =>
				fIdVal.produceErrorMessage(funderIdentifier)
			}
	})
}

object FunderIdentifier{
	def default = FunderIdentifier(Some(""), None)
}

case class FunderIdentifierScheme(funderIdentifierType: String, SchemeURI: Option[String]) {
	override def toString = funderIdentifierType
}

object FunderIdentifierScheme{
	val Crossref = FunderIdentifierScheme("Crossref Funder ID", Some("https://www.crossref.org/services/funder-registry/"))
	val Grid = FunderIdentifierScheme("GRID", Some("https://www.grid.ac/"))
	val Isni = FunderIdentifierScheme("ISNI", Some("http://www.isni.org/"))
	val Ror = FunderIdentifierScheme("ROR", Some("https://ror.org/"))
	val Other = FunderIdentifierScheme("Other", None)

	private val Validators = Map(
		Isni -> FunderIdentifierValidator(
			Isni,
			"""^((http|https):\/\/(www.)?isni.org\/(isni\/)?)?(\d{4} ?){3}\d{3}[0-9X]$""".r,
			"http://www.isni.org/isni/000000012146438X or https://isni.org/isni/000000012146438X or https://isni.org/000000012146438X or 000000012146438X"
			),
		Ror -> FunderIdentifierValidator(
			Ror,
			"^(https://ror.org/)?[a-z0-9]{9}$".r, "https://ror.org/03yrm5c26 or 03yrm5c26"
			),
		Crossref -> FunderIdentifierValidator(
			Crossref,
			"^(https://doi.org/[0-9]{1,2}.[0-9]{5}/)?[1-9]\\d+".r,
			"10.1006/abc or https://doi.org/10.1006/abc"
			),
		Grid -> FunderIdentifierValidator(
			Grid,
			"grid.\\d{4,6}.[0-9a-f]{1,2}".r,
			"grid.238252"
			),
		Other -> FunderIdentifierValidator(
			Other,
			"^(.+)$".r
			)
	)

	def supported = Validators.keys.toSeq

	def lookup(funderIdentifierType: String): Option[FunderIdentifierScheme] =
		Validators.keys.find(_.funderIdentifierType == funderIdentifierType)

	def lookupValidator(funderIdentifierType: String): Option[FunderIdentifierValidator] = {
		lookup(funderIdentifierType).flatMap(t => Validators.get(t))
	}
}


case class FunderIdentifierValidator(scheme: FunderIdentifierScheme, regex: Regex, expectedFormat: String = ""){

	def produceErrorMessage(funderIdentifier: Option[String]): Option[String] = {
		funderIdentifier.fold(Some("Empty funder identifier")){fid =>
			if(regex.matches(fid)) None
			else if(fid.isEmpty) Some("Empty funder identifier")
			else Some(s"Wrong $scheme ID format, examples of accepted IDs: ${expectedFormat}")
		}
	}
}
object NameIdentifierScheme{
	val Orcid = NameIdentifierScheme("ORCID", Some("http://orcid.org/"))
	val Isni = NameIdentifierScheme("ISNI", Some("http://www.isni.org/"))
	val Ror = NameIdentifierScheme("ROR", Some("https://ror.org/"))
	val Fluxnet = NameIdentifierScheme("FLUXNET", None)

	def supported = Regexes.keys.toSeq

	def lookup(nameIdentifierScheme: String): Option[NameIdentifierScheme] =
		Regexes.keys.find(_.nameIdentifierScheme == nameIdentifierScheme)

	def lookupRegex(nameIdentifierScheme: String): Option[Regex] =
		lookup(nameIdentifierScheme).flatMap(Regexes.get)

	private val Regexes = Map(
		Orcid -> """^(\d{4}\-?){3}\d{3}[0-9X]$""".r,
		Isni -> """^(\d{4} ?){3}\d{3}[0-9X]$""".r,
		Ror -> "^[a-z0-9]{9}$".r,
		Fluxnet -> """^[A-Z]{2}\-[A-Z][A-Za-z0-9]{2}$""".r
	)
}

case class Affiliation(name: String)

sealed trait Person extends SelfValidating{
	val name: Name
	val nameIdentifiers: Seq[NameIdentifier]
	val affiliation: Seq[Affiliation]

	def error = joinErrors(
		name.error,
		allGood(nameIdentifiers),
		eachNonEmpty(affiliation.map(_.name))("Affiliation is not required but must not be empty if provided")
	)
}

case class Creator(name: Name, nameIdentifiers: Seq[NameIdentifier], affiliation: Seq[Affiliation]) extends Person

case class FundingReference(
	funderName: Option[String], funderIdentifier: Option[FunderIdentifier],
	awardNumber: Option[String], awardTitle: Option[String], awardUri: Option[String]
) extends SelfValidating {

		def error = joinErrors(
				nonEmpty(funderName)("Funder must have a name"),
				awardUri.flatMap(aUri =>
					Try(new URI(aUri)).failed.toOption.map(_ => s"Invalid funder award URI: $aUri")
				),
				allGood(funderIdentifier),
			)
}
case class Contributor(
	name: Name,
	nameIdentifiers: Seq[NameIdentifier],
	affiliation: Seq[Affiliation],
	contributorType: Option[ContributorType.Value]
) extends Person{

	override def error = joinErrors(
		super.error,
		nonNull(contributorType)("Contributor type must be specified")
	)
}


case class Title(title: String, lang: Option[String], titleType: Option[TitleType.Value]) extends SelfValidating{
	def error = joinErrors(
		nonEmpty(title)("Title must not be empty"),
		lang.flatMap(l => nonEmpty(l)("Title language is not required but must not be empty if provided"))
	)
}

case class ResourceType(resourceType: Option[String], resourceTypeGeneral: Option[ResourceTypeGeneral.Value]) extends SelfValidating{
	def error = joinErrors(
		nonEmpty(resourceType.fold("")(r => r))("Specific resource type must not be empty"),
		nonNull(resourceTypeGeneral)("The general resource type must be specified")
	)
}

case class SubjectScheme(subjectScheme: String, schemeUri: Option[String]) extends SelfValidating{
	def error = joinErrors(
		nonEmpty(subjectScheme)("Subject scheme must have a name"),
		schemeUri.flatMap(validUri)
	)
	override def toString = subjectScheme
}
object SubjectScheme{
	val Dewey = SubjectScheme("Dewey", Some("http://dewey.info/"))
}

case class Subject(
	val subject: String,
	val lang: Option[String] = None,
	val scheme: Option[SubjectScheme] = None,
	val valueUri: Option[String] = None
) extends SelfValidating{
	def error = joinErrors(
		nonEmpty(subject)("Subject must not be empty"),
		lang.flatMap(l => nonEmpty(l)("Subject language is not required but must not be empty if provided")),
		scheme.flatMap(_.error),
		valueUri.flatMap(validUri)
	)
}

case class Date(date: String, dateType: DateType.Value) extends SelfValidating{
	import Date._
	def error = joinErrors(
		nonEmpty(date)("Date must not be empty if specified"),
		nonNull(dateType)("Date type must be specified for every date"),
		if(date == null || date.isEmpty || !dateIsWrong(date)) None
		else Some(s"Wrong date '$date', use format YYYY[-MM-DD]")
	)

	private def dateIsWrong(date: String): Boolean = date match {
		case dateRegex(yearStr, monthStr, dayStr) =>
			val month = monthStr.toInt
			val day = dayStr.toInt
			yearIsWrong(yearStr) || month < 1 || month > 12 || day < 1 || day > 31

		case yearRegex(yearStr) => yearIsWrong(yearStr)
		case _ => true
	}

	private def yearIsWrong(yearStr: String): Boolean =
		try{
			val year = yearStr.toInt
			year < 1900 || year > 3000
		}catch{
			case _: Throwable => true
		}
}

object Date{
	private val dateRegex = """(\d{4})-(\d\d)-(\d\d)""".r
	private val yearRegex = "(\\d{4})".r
}

case class Version(major: Int, minor: Int) extends SelfValidating{

	private def versionCorrect(v: Int, msg: String): Option[String] =
		if(v >= 0 && v < 100) None else Some(msg + " version must be between 0 and 99")

	def error = joinErrors(
		versionCorrect(major, "Major"),
		versionCorrect(minor, "Minor")
	)

	override def toString = s"$major.$minor"
}

object Version{
	val VersionRegex = """^(\d+).(\d+)$""".r
	def parse(s: String): Try[Version] = s match{
		case VersionRegex(major, minor) => Success(Version(major.toInt, minor.toInt))
		case _ => Failure(new IllegalArgumentException(s"Invalid version string: $s"))
	}
}

case class Rights(rights: String, rightsUri: Option[String]) extends SelfValidating{
	def error = joinErrors(
		nonEmpty(rights)("License name must be provided"),
		rightsUri.flatMap(validUri)
	)
}

case class Description(description: String, descriptionType: DescriptionType.Value, lang: Option[String]) extends SelfValidating{
	def error = joinErrors(
		nonEmpty(description)("Description must not be empty (if supplied)"),
		lang.flatMap(l => nonEmpty(l)("Description language is not required but must not be empty if provided")),
		nonNull(descriptionType)("Description type must be specified")
	)
}
