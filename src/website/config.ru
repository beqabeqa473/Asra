require "adsf"
require "rack-livereload"

use Rack::LiveReload
use Adsf::Rack::IndexFileFinder, :root => "output/"
run Rack::File.new("output/")


