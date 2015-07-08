<!DOCTYPE html>
<html>
	<head>
		<meta name="layout" content="main"/>
		<title>BIE Web services | Atlas of Living Australia</title>
	</head>
	<body>
		<div id="page-body" role="main">
			<h1>BIE web services</h1>
			<p>Here is a listing of prototype webservices for BIE</p>

			<table class="table">
				<tr>
					<td>Taxon details</td>
					<td>${g.createLink(controller: 'search', action: 'taxon')}/{GUID}</td>
				</tr>
				<tr>
					<td>Search</td>
					<td>${g.createLink(controller: 'search', action: 'search')}?q={search terms}</td>
				</tr>
				<tr>
					<td>Autocomplete</td>
					<td>${g.createLink(controller: 'search', action: 'auto')}?q={search terms}</td>
				</tr>
			</table>

			<h2>Admin</h2>

			<ul>
				<li><g:link controller="import">Taxonomy import tool</g:link> - import DwC-A with taxonomic information</li>
			</ul>

		</div>
	</body>
</html>
