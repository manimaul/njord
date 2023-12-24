import React from "react";
import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import {Link} from "react-router-dom";
import {NavDropdown} from "react-bootstrap";
import {DepthUnit, Theme} from "../App";
import '../App.css';
import Button from "react-bootstrap/Button";
import {useAdmin} from "../Admin";

type NavLinkProps = {
    path: string,
    label: string,
}

function NavLink(props: NavLinkProps) {
    return (
        <li className="nav-item"><Link to={props.path} className="nav-link">{props.label}</Link></li>
    )
}

type NavBarProps = {
    theme: Theme,
    depths: DepthUnit,
    depthsUpdater: (depths: DepthUnit) => void,
    themeUpdater: (theme: Theme) => void,
}

export function NavBar(props: NavBarProps) {
    const [admin, login, logout] = useAdmin();

    // check for signature in local storage and set login button state based on result
    // if exists try to login automatically & refresh
    return (
        <Navbar className="Wrap" bg="secondary" expand="lg" variant="dark">
            <Container>
                <Navbar.Brand>Njord</Navbar.Brand>
                <Navbar.Toggle aria-controls="basic-navbar-nav"/>
                <Navbar.Collapse id="basic-navbar-nav">
                    <Nav className="me-auto">
                        <NavLink path="/" label="About"/>
                        <NavLink path="/enc" label="ENC"/>
                        <NavLink path="/control/charts_catalog" label="Control Panel"/>
                        <NavDropdown title={"Depths: " + props.depths.toUpperCase()}
                                     id="basic-nav-dropdown">
                            <NavDropdown.Item onClick={() => {
                                props.depthsUpdater(DepthUnit.meters);
                            }}>Meters</NavDropdown.Item>
                            <NavDropdown.Item onClick={() => {
                                props.depthsUpdater(DepthUnit.fathoms);
                            }}>Fathoms & Feet</NavDropdown.Item>
                            <NavDropdown.Item onClick={() => {
                                props.depthsUpdater(DepthUnit.feet);
                            }}>Feet</NavDropdown.Item>
                        </NavDropdown>
                        <NavDropdown title={"Theme: " + props.theme.toUpperCase()}
                                     id="basic-nav-dropdown">
                            <NavDropdown.Item onClick={() => {
                                props.themeUpdater(Theme.day);
                            }}>Day</NavDropdown.Item>
                            <NavDropdown.Item onClick={() => {
                                props.themeUpdater(Theme.dusk);
                            }}>Dusk</NavDropdown.Item>
                            <NavDropdown.Item onClick={() => {
                                props.themeUpdater(Theme.night);
                            }}>Night</NavDropdown.Item>
                        </NavDropdown>
                    </Nav>
                    {admin && <Button
                        variant="danger"
                        onClick={logout}
                    >Disable Admin</Button>}
                    { admin == null && <Button
                        variant="outline-light"
                        onClick={login}
                    >Enable Admin</Button>}
                </Navbar.Collapse>
            </Container>
        </Navbar>
    );
}
