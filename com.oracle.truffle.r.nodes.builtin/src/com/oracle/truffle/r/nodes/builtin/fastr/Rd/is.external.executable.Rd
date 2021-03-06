% Generated by roxygen2: do not edit by hand
% Please edit documentation in R/fastrInteropt.R
\name{is.external.executable}
\alias{is.external.executable}
\title{Determines whether the passed external object can be executed.}
\usage{
is.external.executable(value)
}
\arguments{
\item{value}{an external object}
}
\value{
TRUE in case the given value is executable, otherwise FALSE.
}
\description{
Determines whether the passed external object can be executed.
}
\examples{
javaClass <- new.java.class('java.util.Collections')
is.external.executable(javaClass$addAll())
}
